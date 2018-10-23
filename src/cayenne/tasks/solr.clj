(ns cayenne.tasks.solr
  (:use cayenne.item-tree)
  (:import [org.apache.solr.common SolrInputDocument]
           [org.apache.solr.client.solrj.request CoreAdminRequest]
           [org.apache.solr.common.params CoreAdminParams$CoreAdminAction])
  (:require [clj-time.core :as t]
            [clj-time.format :as df]
            [clojure.string :as string]
            [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi]
            [cayenne.ids.issn :as issn-id]
            [cayenne.ids.isbn :as isbn-id]
            [cayenne.ids :as ids]
            [cayenne.util :as util]
            [clojure.core.async :as async :refer [chan go-loop <! >!! put!]]
            [metrics.gauges :refer [defgauge] :as gauge]
            [metrics.meters :refer [defmeter] :as meter]
            [metrics.timers :refer [deftimer] :as timer]
            [taoensso.timbre :as timbre :refer [error info]]))

(def insert-list (atom []))

(def insert-count (atom 0))

(def inserts-running-count (atom 0))

(def inserts-waiting-chan (chan 10))

(defmeter [cayenne solr insert-events] "insert-events")
(defgauge [cayenne solr inserts-running] @inserts-running-count)
(defgauge [cayenne solr inserts-so-far] @insert-count)
(defgauge [cayenne solr insert-waiting-list-size]
  (count @insert-list))
(deftimer [cayenne solr add-time])
(deftimer [cayenne solr commit-time])

(defn flush-insert-list [insert-list]
  (swap! inserts-running-count inc)
  (info "Starting insert and commit, inserts running = " @inserts-running-count)
  (info "Insert list is" (count insert-list) "items long")
  (doseq [update-server (conf/get-service :solr-update-list)]
    (try
      (let [start-of-update-time (System/currentTimeMillis)]
        (timer/time! add-time (.add update-server insert-list))
        (let [end-of-update-time (System/currentTimeMillis)]
          (info "Solr .add took " (- end-of-update-time start-of-update-time) " milliseconds")
          (when (conf/get-param [:service :solr :commit-on-add])
            (timer/time! commit-time (.commit update-server false false))
            (let [end-of-commit-time (System/currentTimeMillis)]
              (info "Solr .commit took " (- end-of-commit-time end-of-update-time) " milliseconds")))))
      (meter/mark! insert-events)
      (catch Exception e (error e "Solr insert failed" update-server))))
  (swap! inserts-running-count dec)
  (info "Finished insert and commit, inserts running = " @inserts-running-count))

(defn start-insert-list-processing []
  (go-loop []
    (let [insert-list (<! inserts-waiting-chan)]
      (try
        (flush-insert-list insert-list)
        (swap! insert-count inc)
        (catch Exception e (error e "Solr insert go loop error")))
      (recur))))

(conf/with-core :default
  (conf/add-startup-task
   :solr-inserts
   (fn [profiles]
     (start-insert-list-processing))))

(defn get-categories [item]
  (if-let [journal (find-item-of-subtype item :journal)]
    (or (:category journal) [])
    []))

(defn particle->date-time [particle]
  (let [year (-> particle :year util/parse-int-safe)
        month (-> particle :month util/parse-int-safe)
        day (-> particle :day util/parse-int-safe)]
    (cond (and year month day)
          (if (< (t/number-of-days-in-the-month year month) day)
            (t/date-time year month)
            (t/date-time year month day))
          (and year month)
          (t/date-time year month)
          :else
          (t/date-time year))))

(defn get-earliest-pub-date [item]
  (->> (concat
        (get-item-rel item :posted)
        (get-item-rel item :published-print)
        (get-item-rel item :published-online)
        (get-item-rel item :published-other)
        (get-item-rel item :published)
        (get-tree-rel item :content-created))
       (sort-by particle->date-time)
       first))

(defn get-print-or-earliest-pub-date [item]
  (or
   (first (get-tree-rel item :published-print))
   (get-earliest-pub-date item)))

(defn get-contributor-orcids [item]
  (let [contributors (mapcat #(get-item-rel item %) contributor-rels)]
    (remove nil? (mapcat :id contributors))))

(defn get-contributor-affiliations [item]
  (->> contributor-rels
       (mapcat #(get-item-rel item %))
       (mapcat #(get-item-rel % :affiliation))
       (map :name)))

(defn initials [first-name]
  (when first-name
    (string/join " " (map first (string/split first-name #"[\s\-]+")))))

(defn as-name [org-or-person]
  (cond (= :org (get-item-type org-or-person))
        (:name org-or-person)
        (= :person (get-item-type org-or-person))
        (str (:first-name org-or-person) " " (:last-name org-or-person))))

(defn as-initials [org-or-person]
  (cond (= :org (get-item-type org-or-person))
        (as-name org-or-person)
        (= :person (get-item-type org-or-person))
        (str (initials (:first-name org-or-person)) " " (:last-name org-or-person))))

(defn get-contributor-names
  "Contributor names as a concatenated string."
  [item type]
  (let [contributors (get-item-rel item type)]
    (string/join ", " (map as-name contributors))))

(defn as-details [contributor type]
  {:given-name (:first-name contributor)
   :family-name (:last-name contributor)
   :org-name (:name contributor)
   :suffix (:suffix contributor)
   :affiliations (map :name (get-item-rel contributor :affiliation))
   :orcid (first (get-item-ids contributor :orcid))
   :orcid-authenticated (:orcid-authenticated contributor)
   :sequence (:sequence contributor)
   :type (name type)})

(defn get-contributor-details*
  "For each person contributor, return a map of name, ORCID and
   type of contribution."
  [item type]
  (let [contributors (get-item-rel item type)]
    (map as-details contributors (repeat type))))

(defn get-contributor-details [item]
  (concat
   (get-contributor-details* item :author)
   (get-contributor-details* item :chair)
   (get-contributor-details* item :editor)
   (get-contributor-details* item :translator)
   (get-contributor-details* item :contributor)))

(defn get-primary-author [item]
  (first (get-item-rel item :author)))

(defn get-contributors [item]
  (mapcat (partial get-item-rel item) contributor-rels)) ;; todo deal with orgs

(defn get-container-titles [item]
  (mapcat #(get-item-rel % :title) (get-item-rel item :ancestor)))

(defn get-oa-status [item]
  (let [journal (find-item-of-subtype item :journal)]
    (or (:oa-status journal) "Other")))

(defn get-update-policy [item]
  (when-let [policy (first (get-tree-rel item :update-policy))]
    (:value policy)))

(defn get-article-number [item]
  (->> (get-tree-rel item :number)
       (filter #(= "article-number" (:kind %)))
       (map :value)
       first))

(defn get-updates [item]
  (find-items-of-type item :update))

(defn get-assertion-names [item]
  (->> (find-items-of-type item :assertion)
       (filter :name)
       (map :name)
       set))

(defn get-assertion-group-names [item]
  (->> (find-items-of-type item :assertion)
       (filter :group-name)
       (map :group-name)
       set))

(defn as-solr-base-field [item]
  (string/join 
   " "
   (-> []
       (conj (:year (get-earliest-pub-date item))) ; earliest pub year
       (conj (:year (first (get-item-rel item :published-print)))) ; print pub year
       (conj (:issue (find-item-of-subtype item :journal-issue))) ; issue
       (conj (:volume (find-item-of-subtype item :journal-volume))) ; volume
       (conj (:first-page item)) ; pages
       (conj (:last-page item)) ; pages
       (concat (map (comp issn-id/normalize-issn :value) (get-tree-rel item :issn)))
       (concat (map (comp isbn-id/normalize-isbn :value) (get-tree-rel item :isbn)))
       (concat (map :value (get-item-rel item :title))) ; work titles
       (concat (map :value (get-container-titles item)))))) ; publication titles

(defn as-solr-citation-field [item]
  (string/join
   " "
   (-> [(as-solr-base-field item)]
       (concat (map as-initials (get-contributors item)))))) ; names with initials

(defn as-solr-content-field [item]
  (string/join
   " "
   (-> [(as-solr-base-field item)]
       (conj (:description item))
       (concat (map ids/extract-supplementary-id (get-tree-ids item :supplementary))) ; plain supp ids
       (concat (map as-name (get-contributors item))) ; full names
       (concat (mapcat get-item-ids (get-tree-rel item :awarded))) ; grant numbers
       (concat (map :name (get-tree-rel item :funder)))))) ; funder names

(defn as-grant-map [item]
  (letfn [(combine [memo nxt]
            (let [funder-name (:name nxt)
                  awards (get memo funder-name [])
                  new-awards (mapcat :id (get-item-rel nxt :awarded))]
              (assoc memo funder-name (concat awards new-awards))))]
    (reduce combine {} (get-tree-rel item :funder))))

(defn as-license-list 
  "Returns all licenses within an item tree. If the license has no
   explicit start date it is assumed to have a start date equal to
   the preferred published date of the item."
  [item]
  (let [pub-date (get-earliest-pub-date item)
        licenses (get-tree-rel item :license)]
    (map #(if (:start %) % (assoc % :start pub-date))
         licenses)))

(def times [:year :month :day :hour :minute :second])

(defn get-ordered-valid-date [date]
  "Given a map of date values, keys as defined by 'times' [:year :month :day :hour :minute :second]
  return an ordered list of valid numeric values in descending significance"

  (take-while identity (map   cayenne.util/parse-int-safe (map date times)))
)
(defn parse-date-while-ok
  "Expects a vector of date values in descending significance
  return a clj-time datetime with all valid date parts up to the first invalid one.
  2018 2 30 12 15 7 will return 2018-2 date object."
  [date-vec]
  ; Recurse until empty.
  (if (empty? date-vec)
    nil
    (try
      (apply t/date-time date-vec)
      (catch Exception ex
        (parse-date-while-ok (drop-last 1 date-vec))))))

(defn as-datetime [particle-date]
  "Given a map of date part k,v converts vals to numbers and creates
  clj-time date-time object using most significant date parts until one
  fails"
  (parse-date-while-ok (get-ordered-valid-date particle-date))

)

(defn as-datetime-string [particle-date]
  (when-let [dt (as-datetime particle-date)]
    (df/unparse (df/formatters :date-time) dt)))
  
(defn as-day-diff [left-particle-date right-particle-date]
  (let [left (as-datetime left-particle-date)
        right (as-datetime right-particle-date)]
    (if (t/after? left right)
      0
      (-> (t/interval left right)
          (t/in-days)))))

(defn ->license-start-date [license pub-date]
  (let [start-date (first (get-item-rel license :start))]
    (cond start-date
          (as-datetime-string start-date)
          pub-date
          (as-datetime-string pub-date))))

(defn ->license-delay [license pub-date]
  (if-let [start-date (first (get-item-rel license :start))]
    (as-day-diff pub-date start-date)
    0))

(defn as-solr-grant-info-field [item]
  (letfn [(funder-info [funder-name award-ids]
              (str
               funder-name
               " "
               (if-not (empty? award-ids)
                 (str "(" (string/join ", " award-ids) ")")
                 "")))]
    (string/join " | " (for [[k v] (as-grant-map item)] (funder-info k v)))))

(defn as-license-compound [license pub-date]
  (let [license-delay (->license-delay license pub-date)
        license-uri (util/slugify (:value license))
        license-version (:content-version license)]
    {(str "license_version_delay_" license-version) [license-delay]
     (str "license_url_delay_" license-uri) [license-delay]
     (str "license_url_version_" license-uri) [license-version]
     (str "license_url_version_delay_" license-uri "_" license-version) [license-delay]}))

(defn as-full-text-compound [full-text-resource]
  (let [content-type (-> full-text-resource (:content-type) (util/slugify))
        intended-application (-> full-text-resource :intended-application util/slugify)]
    {(str "full_text_type_version_" content-type) [(:content-version full-text-resource)]
     (str "full_text_type_application_" content-type) [(:intended-application full-text-resource)]
     (str "full_text_application_version_" intended-application) [(:content-version full-text-resource)]
     (str "full_text_type_application_version_" content-type "_" intended-application) [(:content-version full-text-resource)]}))

(defn as-award-compound [funder award]
  (let [funder-name (or (:name funder) "-")
        funder-doi (or (-> funder get-item-ids first) "-")
        award-number (or (-> award get-item-ids first) "-")
        normalized-award-number (-> award-number string/lower-case (string/replace #"[\s_\-]+" ""))
        slug-doi (or (-> funder get-item-ids first util/slugify) "-")]
    {(str "award_funder_doi_number_" slug-doi) [normalized-award-number]
     "award_number" [normalized-award-number]
     "award_number_display" [award-number]
     "award_funder_name" [funder-name]
     "award_funder_doi" [funder-doi]}))

(defn as-relation-compound [relation]
  (let [relation-type (-> relation :subtype name)
        object-type (:object-type relation)
        object (:object relation)
        object-namespace (or (:object-namespace relation) "-")
        claimed-by (-> relation :claimed-by name)]
    {"relation_type" [relation-type]
     "relation_object" [object]
     "relation_object_type" [object-type]
     "relation_object_ns" [object-namespace]
     "relation_claimed_by" [claimed-by]
     (str "relation_type_object_" relation-type)
     [object]
     (str "relation_type_object_type_object_" relation-type "_" object-type)
     [object]
     (str "relation_type_object_type_" relation-type)
     [object-type]}))

(defn as-funder-award-compounds [funder]
  (let [awards (map as-award-compound (repeat funder) (get-item-rel funder :awarded))]
    (apply merge-with #(concat %1 %2) awards)))

(defn as-license-compounds [licenses pub-date]
  (let [compounds (map as-license-compound licenses (repeat pub-date))]
    (apply merge-with #(concat %1 %2) compounds)))

(defn as-full-text-compounds [full-text-resources]
  (let [compounds (map as-full-text-compound full-text-resources)]
    (apply merge-with #(concat %1 %2) compounds)))

(defn as-award-compounds [funders]
  (let [compounds (map as-funder-award-compounds funders)]
    (apply merge-with #(concat %1 %2) compounds)))

(defn as-relation-compounds [relations]
  (let [relations (map as-relation-compound relations)]
    (apply merge-with #(concat %1 %2) relations)))

(defn as-contributor-affiliation-lists [contrib-details]
  (into {}
        (map-indexed #(vector (str "contributor_affiliations_" %1)
                              (:affiliations %2))
                     contrib-details)))

(defn as-assertion-list [assertions]
  (->> assertions
       (map-indexed
        #(-> {}
             (util/assoc-str (str "assertion_name_" %1) (:name %2))
             (util/assoc-str (str "assertion_label_" %1) (:label %2))
             (util/assoc-str (str "assertion_group_name_" %1) (:group-name %2))
             (util/assoc-str (str "assertion_group_label_" %1) (:group-label %2))
             (util/assoc-str (str "assertion_url_" %1) (:url %2))
             (util/assoc-str (str "assertion_explanation_url_" %1) (:explanation-url %2))
             (util/assoc-str (str "assertion_value_" %1) (:value %2))
             (util/assoc-int (str "assertion_order_" %1) (:order %2))))
       (apply merge)))

(defn as-issn-types [item]
  (->> (get-tree-rel item :issn)
       (map #(hash-map (str "issn_type_" (-> % :kind name))
                       (:value %)))
       (apply merge)))

(defn as-isbn-types [item]
  (->> (get-tree-rel item :isbn)
       (map #(hash-map (str "isbn_type_" (-> % :kind name))
                       (:value %)))
       (apply merge)))

(defn as-event [item]
  (when-let [event (-> item (get-tree-rel :about) first)]
    (let [start-date (-> event (get-item-rel :start) first)
          end-date (-> event (get-item-rel :end) first)
          event (-> {"event_sponsor" (:sponsor event)}
                    (util/assoc-str "event_name" (:name event))
                    (util/assoc-str "event_theme" (:theme event))
                    (util/assoc-str "event_location" (:location event))
                    (util/assoc-str "event_acronym" (:acronym event))
                    (util/assoc-str "event_number" (:number event)))]
      (cond-> event
        start-date (assoc "event_start_year" (:year start-date))
        start-date (assoc "event_start_month" (:month start-date))
        start-date (assoc "event_start_day" (:day start-date))
        end-date (assoc "event_end_year" (:year end-date))
        end-date (assoc "event_end_month" (:month end-date))
        end-date (assoc "event_end_day" (:day end-date))))))

(defn as-peer-review [item]
  (let [{:keys [running-number revision-round stage recommendation 
                competing-interest-statement review-type language]} (:review item)]
    {"peer_review_running_number" running-number
     "peer_review_revision_round" revision-round
     "peer_review_stage" stage
     "peer_review_recommendation" recommendation
     "peer_review_competing_interest_statement" competing-interest-statement
     "peer_review_type" review-type
     "peer_review_language" language}))

(defn as-citations [item]
  (letfn [(citation-field [f]
            (str "citation_"
                 (-> f
                     name
                     (string/replace "-" "_") )))]
     (-> {}
         (into 
          (map #(vector (citation-field %)
                        (map (util/?- %) (get-tree-rel item :citation)))
               [:key :issn :issn-type :isbn :isbn-type
                :author :volume :issue :first-page :year
                :isbn :isbn-type :edition :component
                :standard-designator :standards-body
                :unstructured :article-title :series-title
                :volume-title :journal-title]))
         (into
          [["citation_doi_asserted_by"
            (->> (get-tree-rel item :citation)
                 (filter :doi)
                 (map #(str (:doi %) "___" (:doi-asserted-by %))))]
           ["citation_doi"
            (map :doi (filter :doi (get-tree-rel item :citation)))]
           ["citation_key_doi"
            (map #(str (:key %) "_" (:doi %))
                 (filter :doi (get-tree-rel item :citation)))]]))))

(defn formatted-now []
  (df/unparse (df/formatters :date-time) (t/now)))

(defn as-solr-document [item]
  (let [grant-map (as-grant-map item)
        licenses (as-license-list item)
        funder-names (set (map :name (get-tree-rel item :funder)))
        funder-dois (set (mapcat :id (get-tree-rel item :funder)))
        publisher (first (get-tree-rel item :publisher))
        full-text-resources (get-item-rel item :resource-fulltext)
        funders (get-tree-rel item :funder)
        institutions (get-tree-rel item :institution)
        relations (get-tree-rel item :relation)
        assertions (get-tree-rel item :assertion)
        clinical-trial-numbers (get-tree-rel item :clinical-trial-number)
        pub-date (get-earliest-pub-date item)

        ;; print pub date is explicit or default
        print-pub-date (or (first (get-item-rel item :published-print))
                           (first (get-item-rel item :published)))
        
        online-pub-date (first (get-item-rel item :published-online))
        accepted-date (first (get-item-rel item :accepted))
        posted-date (first (get-item-rel item :posted))
        content-created-date (first (get-tree-rel item :content-created))
        content-updated-date (first (get-tree-rel item :content-updated))
        approved-date (first (get-tree-rel item :approved))
        primary-author (get-primary-author item)
        container-titles (get-container-titles item)
        deposit-date (first (get-tree-rel item :deposited))
        first-deposit-date (first (get-tree-rel item :first-deposited))
        free-to-read-start-date (first (get-tree-rel item :free-to-read-start))
        free-to-read-end-date (first (get-tree-rel item :free-to-read-end))
        standards-body (first (get-tree-rel item :standards-body))
        contrib-details (get-contributor-details item)
        updates (get-updates item)
        doi (first (get-item-ids item :long-doi))
        journal-issue (find-item-of-subtype item :journal-issue)]
    (-> {"source" (:source item)
         "indexed_at" (formatted-now)
         "deposited_at" (if deposit-date (as-datetime-string deposit-date) (formatted-now))
         "first_deposited_at"
         (or (when first-deposit-date (as-datetime-string first-deposit-date))
             (when deposit-date (as-datetime-string deposit-date))
             (formatted-now))
         "_version_" 0
         "prefix" (doi/extract-long-prefix doi)
         "doi_key" doi
         "doi" doi
         "issn" (get-tree-ids item :issn)
         "isbn" (get-tree-ids item :isbn)
         "supplementary_id" (get-tree-ids item :supplementary)
         "orcid" (get-contributor-orcids item)
         "article_number" (get-article-number item)
         "affiliation" (get-contributor-affiliations item)
         "hl_affiliation" (get-contributor-affiliations item)
         "assertion_name" (get-assertion-names item)
         "assertion_group_name" (get-assertion-group-names item)
         "category" (get-categories item)
         "funder_name" funder-names
         "funder_doi" funder-dois
         "type" (subtype-labels (get-item-subtype item))
         "first_author_given" (:first-name primary-author)
         "first_author_surname" (:last-name primary-author)
         "content" (as-solr-content-field item)
         "content_citation" (as-solr-citation-field item)
         "content_type" (:content-type item)
         "publication" (->> container-titles
                            (filter #(= (:subtype %) :long))
                            (map :value))
         "standards_body_name" (:name standards-body)
         "standards_body_acronym" (:acronym standards-body)
         "oa_status" (get-oa-status item)
         "hl_publication" (->> container-titles
                               (filter #(= (:subtype %) :long))
                               (map :value))
         "hl_short_publication" (->> container-titles
                                     (filter #(= (:subtype %) :short))
                                     (map :value))
         "year" (:year pub-date)
         "month" (:month pub-date)
         "day" (:day pub-date)
         "print_year" (:year print-pub-date)
         "print_month" (:month print-pub-date)
         "print_day" (:day print-pub-date)
         "online_year" (:year online-pub-date)
         "online_month" (:month online-pub-date)
         "online_day" (:day online-pub-date)
         "posted_year" (:year posted-date)
         "posted_month" (:month posted-date)
         "posted_day" (:day posted-date)
         "accepted_year" (:year accepted-date)
         "accepted_month" (:month accepted-date)
         "accepted_day" (:day accepted-date)
         "free_to_read_start_year" (:year free-to-read-start-date)
         "free_to_read_start_month" (:month free-to-read-start-date)
         "free_to_read_start_day" (:day free-to-read-start-date)
         "free_to_read_end_year" (:year free-to-read-end-date)
         "free_to_read_end_month" (:month free-to-read-end-date)
         "free_to_read_end_day" (:day free-to-read-end-date)
         "issue_online_year" (:year (:published-online journal-issue))
         "issue_online_month" (:month (:published-online journal-issue))
         "issue_online_day" (:day (:published-online journal-issue))
         "issue_print_year" (:year (:published-print journal-issue))
         "issue_print_month" (:month (:published-print journal-issue))
         "issue_print_day" (:day (:published-print journal-issue))
         "content_created_year" (:year content-created-date)
         "content_created_month" (:month content-created-date)
         "content_created_day" (:day content-created-date)
         "content_updated_year" (:year content-updated-date)
         "content_updated_month" (:month content-updated-date)
         "content_updated_day" (:day content-updated-date)
         "approved_year" (:year approved-date)
         "approved_month" (:month approved-date)
         "approved_day" (:day approved-date)
         "contributor_given_name" (map (util/?- :given-name) contrib-details)
         "contributor_family_name" (map (util/?- :family-name) contrib-details)
         "contributor_org_name" (map (util/?- :org-name) contrib-details)
         "contributor_suffix" (map (util/?- :suffix) contrib-details)
         "contributor_orcid" (map (util/?- :orcid) contrib-details)
         "contributor_orcid_authed" (map (util/?- :orcid-authenticated) contrib-details)
         "contributor_type" (map (util/?- :type) contrib-details)
         "contributor_sequence" (map (util/?- :sequence) contrib-details)
         "hl_description" (:description item)
         "hl_year" (:year pub-date)
         "hl_authors" (get-contributor-names item :author)
         "hl_editors" (get-contributor-names item :editor)
         "hl_chairs" (get-contributor-names item :chair)
         "hl_translators" (get-contributor-names item :translator)
         "hl_contributors" (get-contributor-names item :contributor)
         "hl_first_page" (:first-page item)
         "hl_last_page" (:last-page item)
         "hl_funder_name" funder-names
         "hl_grant" (as-solr-grant-info-field item)
         "hl_issue" (:issue journal-issue)
         "hl_volume" (:volume (find-item-of-subtype item :journal-volume))
         "hl_group_title" (->> (get-item-rel item :title)
                               (filter #(= (:subtype %) :group))
                               (map :value)
                               first)
         "hl_title" (->> (get-item-rel item :title)
                         (filter #(= (:subtype %) :long))
                         (map :value))
         "hl_short_title" (->> (get-item-rel item :title)
                               (filter #(= (:subtype %) :short))
                               (map :value))
         "hl_original_title" (->> (get-item-rel item :title)
                                  (filter #(= (:subtype %) :original))
                                  (map :value))
         "hl_subtitle" (->> (get-item-rel item :title)
                            (filter #(= (:subtype %) :secondary))
                            (map :value))
         "archive" (map :name (get-tree-rel item :archived-with))
         "degree" (map :value (get-item-rel item :degree))
         "license_url" (map (util/?- :value) licenses)
         "license_version" (map (util/?- :content-version) licenses)
         "license_start" (map ->license-start-date licenses (repeat pub-date))
         "license_delay" (map ->license-delay licenses (repeat pub-date))
         "references" false ;now
         "cited_by_count" (get-tree-rel item :cited-count)
         "citation_count" (count (get-tree-rel item :citation))
         "full_text_type" (map (util/?- :content-type) full-text-resources)
         "full_text_url" (map (util/?- :value) full-text-resources)
         "full_text_version" (map (util/?- :content-version) full-text-resources)
         "full_text_application" (map (util/?- :intended-application) full-text-resources)
         "edition_number" (:edition-number (find-first-item-of-subtypes item [:edited-book :monograph :reference-book :book]))
         "part_number" (:part-number (find-item-of-subtype item :book-set))
         "publisher" (:name publisher)
         "publisher_str" (:name publisher)
         "hl_publisher" (:name publisher)
         "publisher_location" (:location publisher)
         "owner_prefix" (or (first (get-item-ids publisher :owner-prefix)) "none")
         "member_id" (or (first (get-item-ids publisher :member)) "none")
         "update_policy" (get-update-policy item)
         "update_doi" (map :value updates)
         "update_type" (map :subtype updates)
         "update_label" (map :label updates)
         "update_date" (map #(-> (get-item-rel % :updated) first as-datetime-string) updates)
         "funder_record_name" (map (util/?- :name) funders)
         "funder_record_doi_asserted_by" (map (util/?- :doi-asserted-by) funders)
         "funder_record_doi" (map (util/?fn- (comp first get-item-ids)) funders)
         "institution_name" (map (util/?- :name) institutions)
         "institution_acronym" (map (util/?- :acronym) institutions)
         "institution_location" (map (util/?- :location) institutions)
         "institution_department" (map :name (flatten (map #(get-item-rel % :component) institutions)))
         "domain_exclusive" (or (first (get-item-rel item :domain-exclusive)) false)
         "domains" (get-item-rel item :domains)
         "language" (:language (find-item-of-subtype item :journal))
         "abstract" (-> item (get-item-rel :abstract) first :plain)
         "abstract_xml" (-> item (get-item-rel :abstract) first :xml)
         "clinical_trial_number_ctn" (map :ctn clinical-trial-numbers)
         "clinical_trial_number_registry" (map :registry clinical-trial-numbers)
         "clinical_trial_number_type" (map (util/?- :ctn-type) clinical-trial-numbers)
         "clinical_trial_number_proxy" (map #(-> % :ctn cayenne.ids.ctn/ctn-proxy) clinical-trial-numbers)}

        (merge (as-peer-review item))
        (merge (as-citations item))
        (merge (as-event item))
        (merge (as-isbn-types item))
        (merge (as-issn-types item))
        (merge (as-assertion-list assertions))
        (merge (as-contributor-affiliation-lists contrib-details))
        (merge (as-relation-compounds relations))
        (merge (as-award-compounds funders))
        (merge (as-license-compounds licenses pub-date))
        (merge (as-full-text-compounds full-text-resources)))))

(defn as-solr-field-names [solr-doc]
  (->> (.getFieldNames solr-doc)
       (filter #(let [field-values (.getFieldValues solr-doc %)]
                  (not (or (nil? field-values)
                           (.isEmpty field-values)))))))

(defn as-solr-input-document [solr-map]
  (let [doc (SolrInputDocument. (into-array String []))]
    (doseq [[k v] solr-map]
      (.addField doc k v))
    (.addField doc "field_names" (vec (as-solr-field-names doc)))
    doc))

(defn as-cited-count-set-document [subject-doi cited-count]
  (let [doc (SolrInputDocument. (into-array String []))]
    (.addField doc "doi_key" (doi/to-long-doi-uri subject-doi))

    ;; only apply update if doi_key already exists in index
    (.addField doc "_version_" 1)
    
    (.addField doc "indexed_at" (java.util.HashMap. {"set" (formatted-now)}))
    (.addField doc "cited_by_count" (java.util.HashMap. {"set" cited-count}))
    doc))

(defn as-citation-doi-set-document [subject-doi subject-citation-id object-doi]
  (let [doc (SolrInputDocument. (into-array String []))]
    (.addField doc "doi_key" (doi/to-long-doi-uri subject-doi))
    (.addField doc "_version_" 1)
    (.addField doc "indexed_at" (java.util.HashMap. {"set" (formatted-now)}))
    (.addField doc "citation_key_doi" {"add" (str subject-citation-id "_" object-doi)})
    (.addField doc "citation_doi_asserted_by" {"add" (str object-doi "___crossref")})
    (.addField doc "citation_doi" {"add" object-doi})
    doc))

(defn insert-solr-doc [solr-doc]
  (swap! insert-list
         #(if (>= (count %)
                  (conf/get-param [:service :solr :insert-list-max-size]))
            (do
              (put! inserts-waiting-chan (conj % solr-doc))
              [])
            (conj % solr-doc))))
     
(defn insert-item [item]
  (let [solr-map (as-solr-document item)]
    (if-not (get solr-map "doi_key")
      (throw (Exception. "No DOI in item tree when inserting into solr."))
      (let [solr-doc (as-solr-input-document solr-map)]
        (insert-solr-doc solr-doc)))))


