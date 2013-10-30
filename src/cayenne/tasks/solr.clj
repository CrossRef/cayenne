(ns cayenne.tasks.solr
  (:use cayenne.item-tree)
  (:import [org.apache.solr.common SolrInputDocument]
           [org.apache.solr.client.solrj.request CoreAdminRequest]
           [org.apache.solr.common.params CoreAdminParams$CoreAdminAction])
  (:require [clj-time.core :as t]
            [clojure.string :as string]
            [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi]
            [cayenne.ids :as ids]
            [cayenne.util :as util]
            [metrics.gauges :refer [defgauge] :as gauge]
            [taoensso.timbre :as timbre :refer [error info]]))

(def insert-list (atom []))

(def insert-count (agent 0))

(defgauge [cayenne solr insert-waiting-list-size]
  (count @insert-list))

(defgauge [cayenne solr commit-waiting-list-size]
  (let [request (doto (CoreAdminRequest.)
                  (.setCoreName (conf/get-param [:service :solr :insert-core]))
                  (.setAction CoreAdminParams$CoreAdminAction/STATUS))
        response (-> (.process request (conf/get-service :solr))
                     (.getCoreStatus))]
    (info response)))

(defn flush-insert-list [c insert-list]
  (doseq [update-server (conf/get-service :solr-update-list)]
    (try
      (.add update-server insert-list)
      (.commit update-server false false)
      (catch Exception e (error e "Solr insert failed" update-server))))
  (inc c))

(defn add-to-insert-list [insert-list doc]
  (if (>= (count insert-list)
           (conf/get-param [:service :solr :insert-list-max-size]))
    (do
      (send-off insert-count flush-insert-list insert-list)
      [doc])
    (conj insert-list doc)))

(defn clear-insert-list []
  (dosync
   (alter insert-list (constantly []))))

(defn flush-commit-swap []
  (info "Final solr insert list flush...")
  (flush-insert-list)
  (info "Performing a SOLR commit...")
  (.commit (conf/get-service :solr-update) true true)
  (info "Swapping SOLR cores...")
  (doto (CoreAdminRequest.)
    (.setCoreName (conf/get-param [:service :solr :insert-core]))
    (.setOtherCoreName (conf/get-param [:service :solr :query-core]))
    (.setAction CoreAdminParams$CoreAdminAction/SWAP)
    (.process (conf/get-service :solr))))

(defn get-categories [item]
  (if-let [journal (find-item-of-subtype item :journal)]
    (or (:category journal) [])
    []))

(defn get-preferred-pub-date [item]
  (or
   (first (get-tree-rel item :published-print))
   (first (get-tree-rel item :published-online))))

(defn get-contributor-orcids [item]
  (let [contributors (mapcat #(get-item-rel item %) contributor-rels)]
    (filter (complement nil?) (mapcat :id contributors))))

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
   :suffix (:suffix contributor)
   :orcid (first (get-item-ids contributor :orcid))
   :type type})

(defn get-contributor-details*
  "For each person contributor, return a map of name, ORCID and
   type of contribution."
  [item type]
  (let [contributors (filter #(= (get-item-type %) :person) 
                             (get-item-rel item type))]
    (map as-details contributors)))

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
  (let [titles (get-descendant-rel item :title)]
    (map :value titles)))

(defn get-oa-status [item]
  (let [journal (find-item-of-subtype item :journal)]
    (or (:oa-status journal) "Other")))

(defn as-solr-base-field [item]
  (string/join 
   " "
   (-> []
       (conj (:year (get-preferred-pub-date item))) ; year
       (conj (:issue (find-item-of-subtype item :journal-issue))) ; issue
       (conj (:volume (find-item-of-subtype item :journal-volume))) ; volume
       (conj (:first-page item)) ; pages
       (conj (:last-page item)) ; pages
       (concat (map :value (get-item-rel item :title))) ; work titles
       (concat (get-container-titles item))))) ; publication titles

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
                  awards (or (get memo funder-name) [])
                  new-awards (mapcat :id (get-item-rel nxt :awarded))]
              (assoc memo funder-name (concat awards new-awards))))]
    (reduce combine {} (get-tree-rel item :funder))))

(defn as-license-list 
  "Returns all licenses within an item tree. If the license has no
   explicit start date it is assumed to have a start date equal to
   the preferred published date of the item."
  [item]
  (let [pub-date (get-preferred-pub-date item)
        licenses (get-tree-rel item :license)]
    (map #(if (:start %) % (assoc % :start pub-date))
         licenses)))

(defn as-datetime [particle-date]
  (cond (:day particle-date)
        (t/date-time (:year particle-date)
                     (:month particle-date)
                     (:day particle-date))
        (:month particle-date)
        (t/date-time (:year particle-date)
                     (:month particle-date))
        :else
        (t/date-time (:year particle-date))))

(defn as-day-diff [left-particle-date right-particle-date]
  (-> (t/interval (as-datetime left-particle-date) 
                  (as-datetime right-particle-date))
      (t/in-days)))

(defn ->license-start-date [license pub-date]
  (if-let [start-date (first (get-item-rel license :start))]
    (as-datetime start-date)
    (as-datetime pub-date)))

(defn ->license-delay [pub-date license]
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
  (let [license-delay (->license-delay pub-date license)
        license-uri (util/simplify-uri (:value license))
        license-version (:content-version license)]
    {(str "license_version_to_delay_" license-version) [license-delay]
     (str "license_url_to_delay_" license-uri) [license-delay]
     (str "license_url_to_version_" license-uri) [license-version]
     (str "license_url_and_version_to_delay_" license-uri license-version) [license-delay]}))

(defn as-full-text-compound [full-text-resource]
  {(str "full_text_type_to_version_" (:content-type full-text-resource))
   [(:content-version full-text-resource)]})

(defn as-license-compounds [licenses pub-date]
  (let [compounds (map as-license-compound licenses (repeat pub-date))]
    (apply merge-with #(concat %1 %2) compounds)))

(defn as-full-text-compounds [full-text-resources]
  (let [compounds (map as-full-text-compound full-text-resources)]
    (apply merge-with #(concat %1 %2) compounds)))

(defn as-solr-document [item]
  (let [grant-map (as-grant-map item)
        licenses (as-license-list item)
        funder-names (set (map :name (get-tree-rel item :funder)))
        funder-dois (set (mapcat :id (get-tree-rel item :funder)))
        publisher (first (get-tree-rel item :publisher))
        full-text-resources (get-item-rel item :resource-fulltext)
        pub-date (get-preferred-pub-date item)
        primary-author (get-primary-author item)
        container-titles (get-container-titles item)
        deposit-date (first (get-tree-rel item :deposited))
        contrib-details (get-contributor-details item)
        doi (first (get-item-ids item :long-doi))]
    (-> {"source" (:source item)
         "indexed_at" (t/now)
         "deposited_at" (if deposit-date (as-datetime deposit-date) (t/now))
         "prefix" (doi/extract-long-prefix doi)
         "doi_key" doi
         "doi" doi
         "issn" (get-tree-ids item :issn)
         "isbn" (get-tree-ids item :isbn)
         "supplementary_id" (get-tree-ids item :supplementary)
         "orcid" (get-contributor-orcids item)
         "category" (get-categories item)
         "funder_name" funder-names
         "funder_doi" funder-dois
         "type" (subtype-labels (get-item-subtype item))
         "first_author_given" (:first-name primary-author)
         "first_author_surname" (:last-name primary-author)
         "content" (as-solr-content-field item)
         "content_citation" (as-solr-citation-field item)
         "publication" container-titles
         "oa_status" (get-oa-status item)
         "hl_publication" container-titles
         "year" (:year pub-date)
         "month" (:month pub-date)
         "day" (:day pub-date)
         "contributor_given_name" (map :given-name contrib-details)
         "contributor_family_name" (map :family-name contrib-details)
         "contributor_suffix" (map :suffix contrib-details)
         "contributor_orcid" (map :orcid contrib-details)
         "contributor_type" (map :type contrib-details)
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
         "hl_issue" (:issue (find-item-of-subtype item :journal-issue))
         "hl_volume" (:volume (find-item-of-subtype item :journal-volume))
         "hl_title" (map :value (get-item-rel item :title))
         "archive" nil ;later
         "license_url" (map :value licenses)
         "license_version" (map :content-version licenses)
         "license_start" (map ->license-start-date licenses (repeat pub-date))
         "license_delay" (map ->license-delay licenses)
         "references" false ;now
         "cited_by_count" 0 ;now
         "full_text_type" (map :content-type full-text-resources)
         "full_text_url" (map :value full-text-resources)
         "full_text_version" (map :content-version full-text-resources)
         "publisher" (:name publisher)
         "hl_publisher" (:name publisher)
         "owner_prefix" (or (first (get-item-ids publisher :owner-prefix)) "none")}
        (merge (as-license-compounds licenses pub-date))
        (merge (as-full-text-compounds full-text-resources)))))

;; TODO missing - license_version, full_text_version

(defn as-solr-input-document [solr-map]
  (let [doc (SolrInputDocument.)]
    (doseq [[k v] solr-map]
      (.addField doc k v))
    doc))
     
(defn insert-item [item]
  (let [solr-map (as-solr-document item)]
    (if-not (get solr-map "doi_key")
      (throw (Exception. "No DOI in item tree when inserting into solr."))
      (let [solr-doc (as-solr-input-document solr-map)]
        (swap! insert-list add-to-insert-list solr-doc)))))

  
