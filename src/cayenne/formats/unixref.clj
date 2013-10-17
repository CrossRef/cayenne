(ns cayenne.formats.unixref
  (:require [clj-time.format :as ftime]
            [clj-time.core :as t]
            [cayenne.xml :as xml]
            [cayenne.conf :as conf]
            [cayenne.ids :as ids]
            [cayenne.ids.fundref :as fundref]
            [cayenne.item-tree :as itree]
            [clojure.tools.trace :as trace]
            [taoensso.timbre :as timbre :refer [info error]])
  (:use [cayenne.util :only [?> ?>>]])
  (:use [cayenne.ids.doi :only [to-long-doi-uri]])
  (:use [cayenne.ids.issn :only [to-issn-uri]])
  (:use [cayenne.ids.isbn :only [to-isbn-uri]])
  (:use [cayenne.ids.orcid :only [to-orcid-uri]]))

;; -----------------------------------------------------------------
;; Helpers

;; todo rename to parse-attach-rel
(defn parse-attach
  "Attach a relation by running a function on an xml location."
  [item relation loc kind parse-fn]
  (let [existing (or (get item relation) [])
        related (parse-fn loc)]
    (cond 
     (= kind :single)
     (if (not (nil? related))
       (assoc-in item [:rel relation] (conj existing related))
       item)
     (= kind :multi)
     (if (and (not (nil? related)) (not (empty? related)))
       (assoc-in item [:rel relation] (concat existing related))
       item))))

(defn attach-rel
  "Attach related item to another item via relation."
  [item relation related-item]
  (let [existing (or (get item relation) [])]
    (assoc-in item [:rel relation] (conj existing related-item))))

(defn attach-id
  "Attach URI ids to an item."
  [item id]
  (if id
    (let [existing (or (get item :id) [])]
      (assoc item :id (conj existing id)))
    item))

(defn attach-ids
  [item ids]
  (if (not (empty? ids))
    (let [existing (or (get item :id) [])]
      (assoc item :id (concat existing ids)))
    item))

(defn append-items
  "Appends a map, or concats a list of maps, to an existing item list."
  [a b]
  (if (map? b)
    (conj a b)
    (concat a b)))

(defn if-conj
  "Conj item to coll iff item is not nil."
  [coll item]
  (if (not (nil? item))
    (conj coll item)
    coll))

;; -----------------------------------------------------------------
;; Component locators

(defn find-journal [record-loc]
  (xml/xselect1 record-loc :> "journal"))

(defn find-journal-metadata [journal-loc]
  (xml/xselect1 journal-loc "journal_metadata"))

(defn find-journal-issue [journal-loc]
  (xml/xselect1 journal-loc "journal_issue"))

(defn find-journal-article [journal-loc]
  (xml/xselect1 journal-loc "journal_article"))

(defn find-journal-volume [journal-loc]
  (xml/xselect1 journal-loc "journal_issue" "journal_volume"))

(defn find-conf [record-loc]
  (xml/xselect1 record-loc :> "conference"))

(defn find-proceedings [conf-loc]
  (xml/xselect1 conf-loc "proceedings_metadata"))

(defn find-event
  "Sometimes present in a proceedings. Never has a DOI."
  [conf-loc]
  (xml/xselect1 conf-loc "event_metadata"))

(defn find-conf-paper
  "Found in conferences."
  [conf-loc]
  (xml/xselect1 conf-loc "conference_paper"))

(defn find-book [record-loc]
  (xml/xselect1 record-loc :> "book"))

(defn find-dissertation [record-loc]
  (xml/xselect1 record-loc :> "dissertation"))

(defn find-report [record-loc]
  (xml/xselect1 record-loc :> "report-paper"))

(defn find-standard [record-loc]
  (xml/xselect1 record-loc :> "standard"))

(defn find-database [record-loc]
  (xml/xselect1 record-loc :> "database"))

(defn find-stand-alone-component [record-loc]
  (xml/xselect1 record-loc :> "sa_component"))

;; --------------------------------------------------------------------
;; Dates

(defn find-pub-dates [work-loc kind]
  (xml/xselect work-loc "publication_date" [:= "media_type" kind]))

(defn find-approval-dates [work-loc kind]
  (xml/xselect work-loc "approval_date" [:= "media_type" kind]))

(defn find-event-date [event-loc]
  (xml/xselect1 event-loc "conference_date"))

(defn parse-month [month]
  (if-let [month-val (try (Integer/parseInt month) (catch Exception _ nil))]
    (if (and (>= month-val 1) (<= month-val 12))
      month-val
      nil)))

(defn parse-time-of-year [month]
  (if-let [month-val (try (Integer/parseInt month) (catch Exception _ nil))]
    (cond
     (= month-val 21) :spring
     (= month-val 22) :summer
     (= month-val 23) :autumn
     (= month-val 24) :winter
     (= month-val 31) :first-quarter
     (= month-val 32) :second-quarter
     (= month-val 33) :third-quarter
     (= month-val 34) :forth-quarter
     :else nil)))

(defn parse-date
  "Parse 'print' or 'online' publication dates."
  [date-loc]
  (let [day-val (xml/xselect1 date-loc "day" :text)
        month-val (xml/xselect1 date-loc "month" :text)
        year-val (xml/xselect1 date-loc "year" :text)]
    {:type :date
     :day day-val 
     :month (parse-month month-val) 
     :year year-val
     :time-of-year (parse-time-of-year month-val)}))

(defn parse-start-date [start-date-loc]
  (let [month-val (xml/xselect1 start-date-loc "start_month" :text)]
    {:type :date
     :day (xml/xselect1 start-date-loc "start_day" :text)
     :month (parse-month month-val)
     :year (xml/xselect1 start-date-loc "start_year" :text)
     :time-of-year (parse-time-of-year month-val)}))

(defn parse-end-date [end-date-loc]
  (let [month-val (xml/xselect1 end-date-loc "end_month" :text)]
    {:type :date
     :day (xml/xselect1 end-date-loc "end_day" :text)
     :month (parse-month month-val)
     :year (xml/xselect1 end-date-loc "end_year" :text)
     :time-of-year (parse-time-of-year month-val)}))

;; --------------------------------------------------------------
;; Citations

(defn find-citations [work-loc]
  (xml/xselect work-loc "citation_list" "citation"))

(defn parse-citation [citation-loc]
  (-> {:display-doi (xml/xselect1 citation-loc "doi" :text)
       :issn (to-issn-uri (xml/xselect1 citation-loc "issn" :text))
       :journal-title (xml/xselect1 citation-loc "journal_title" :text)
       :author (xml/xselect1 citation-loc "author" :text)
       :volume (xml/xselect1 citation-loc "volume" :text)
       :issue (xml/xselect1 citation-loc "issue" :text)
       :first-page (xml/xselect1 citation-loc "first_page" :text)
       :year (xml/xselect1 citation-loc "cYear" :text)
       :isbn (to-isbn-uri (xml/xselect1 citation-loc "isbn" :text))
       :series-title (xml/xselect1 citation-loc "series_title" :text)
       :volume-title (xml/xselect1 citation-loc "volume_title" :text)
       :edition-number (xml/xselect1 citation-loc "edition_number" :text)
       :component-number (xml/xselect1 citation-loc "component_number" :text)
       :article-title (xml/xselect1 citation-loc "article_title" :text)
       :unstructured (xml/xselect1 citation-loc "unstructured_citation" :text)}
      (attach-id (to-long-doi-uri (xml/xselect1 citation-loc "doi" :text)))))

(defn parse-citation-ids [citation-loc]
  {:doi (to-long-doi-uri (xml/xselect1 citation-loc "doi" :text))})

;; ---------------------------------------------------------------------
;; Resources

(defn parse-collection-item 
  "Returns a resource link. :content-version can be any of tdm, vor, am or unspecified."
  [coll-item-loc]
  {:type :url
   :content-version (or (xml/xselect1 content-item-loc "resource" ["content_version"])
                        "unspecified")
   :content-type (or (xml/xselect1 coll-item-loc "resource" ["mime_type"]) "*/*")
   :value (xml/xselect1 coll-item-loc "resource" :text)})

(defn parse-collection [with-attribute item-loc]
  (let [items (xml/xselect item-loc 
                           "doi_data" :> "collection"
                           [:= "property" with-attribute] "item")]
    (map parse-collection-item items)))

(defn parse-resource [item-loc]
  (when-let [value (xml/xselect1 item-loc "doi_data" "resource" :text)]
    {:type :url
     :value value}))

;; ----------------------------------------------------------------------
;; Titles

(defn parse-journal-title [full-title-loc kind]
  {:type :title :subtype kind :value (xml/xselect1 full-title-loc :text)})

(defn parse-full-titles [item-loc]
  (map #(parse-journal-title % :long) (xml/xselect item-loc "full_title")))

(defn parse-abbrev-titles [item-loc]
  (map #(parse-journal-title % :short) (xml/xselect item-loc "abbrev_title")))

(defn parse-proceedings-title [item-loc]
  (if-let [title (xml/xselect1 item-loc "proceedings_title" :text)]
    {:type :title :subtype :long :value title}))

(defn parse-title [item-loc]
  (when-let [title (xml/xselect1 item-loc "titles" "title" :text)]
    {:type :title :subtype :long :value title}))

(defn parse-subtitle [item-loc]
  (if-let [title (xml/xselect1 item-loc "titles" "subtitle" :text)]
    {:type :title :subtype :secondary :value title}))

(defn parse-language-title [item-loc]
  (if-let [title-loc (xml/xselect1 item-loc "titles" "original_language_title")]
    {:type :title
     :subtype :long
     :language (xml/xselect1 title-loc ["language"])
     :value (xml/xselect1 title-loc :text)}))

;; --------------------------------------------------------------
;; Identifiers

(defn parse-doi [item-loc]
  (if-let [doi (xml/xselect1 item-loc "doi_data" "doi" :text)]
    {:type :id
     :subtype :doi
     :ra :crossref
     :value (to-long-doi-uri doi)
     :original doi}))

(defn parse-doi-uri [item-loc]
  (if-let [doi (xml/xselect1 item-loc "doi_data" "doi" :text)]
    (to-long-doi-uri doi)))

(defn find-issns [item-loc]
  (xml/xselect item-loc "issn"))

(defn parse-issn [issn-loc]
  (let [issn-type (or (xml/xselect1 issn-loc ["media_type"]) "print")
        issn-value (xml/xselect1 issn-loc :text)]
    {:type :id
     :subtype :issn
     :kind (if (= issn-type "print") :print :electronic)
     :value (to-issn-uri issn-value)
     :original issn-value}))

(defn parse-issn-uri [issn-loc]
  (if-let [issn (xml/xselect1 issn-loc :text)]
    (to-issn-uri issn)))

(defn find-isbns [item-loc]
  (xml/xselect item-loc "isbn"))

;; todo normalize isbn
(defn parse-isbn [isbn-loc]
  (let [isbn-type (or (xml/xselect1 isbn-loc ["media_type"]) "print")
        isbn-value (xml/xselect1 isbn-loc :text)]
    {:type :id
     :subtype :isbn
     :kind (if (= isbn-type "print") :print :electronic)
     :value (to-isbn-uri isbn-value)
     :original isbn-value}))

(defn parse-isbn-uri [isbn-loc]
  (if-let [isbn (xml/xselect1 isbn-loc :text)]
    (to-isbn-uri isbn)))

(defn parse-supplementary-id-uri [domain-id-loc]
  (let [id-type (xml/xselect1 domain-id-loc ["id-type"])
        id-val (xml/xselect1 domain-id-loc :text)]
    (ids/to-supplementary-id-uri id-val)))

(defn find-supplementary-ids [item-loc]
  (xml/xselect item-loc "publisher_item" "identifier"))

;; ---------------------------------------------------------------
;; Contributors

;; todo authenticated should be a property of the relation
(defn parse-orcid [person-loc]
  (when-let [orcid-loc (xml/xselect1 person-loc "ORCID")]
    {:type :id
     :subtype :orcid
     :authenticated (or (xml/xselect1 orcid-loc ["authenticated"]) "false")
     :value (to-orcid-uri (xml/xselect1 orcid-loc :text))
     :original (xml/xselect1 orcid-loc :text)}))

(defn parse-orcid-uri [person-loc]
  (when-let [orcid-loc (xml/xselect1 person-loc "ORCID")]
    (to-orcid-uri (xml/xselect1 orcid-loc :text))))
     
;; todo can have location after a comma
(defn parse-affiliation [affiliation-loc]
  {:type :org
   :name (xml/xselect1 affiliation-loc :text)})

(defn find-affiliations [person-loc]
  (xml/xselect person-loc "affiliation"))

(defn parse-person-name [person-loc]
  (let [person {:type :person
                :first-name (xml/xselect1 person-loc "given_name" :text)
                :last-name (xml/xselect1 person-loc "surname" :text)
                :suffix (xml/xselect1 person-loc "suffix" :text)}
        parse-fn #(map parse-affiliation (find-affiliations %))]
    (-> person
        (attach-id (parse-orcid-uri person-loc))
        (parse-attach :affiliation person-loc :multi parse-fn))))

(defn parse-organization [org-loc]
  {:type :org
   :name (xml/xselect1 org-loc :text)})

(defn find-person-names [item-loc kind]
  (xml/xselect item-loc "contributors" "person_name" [:= "contributor_role" kind]))

(defn find-organizations [item-loc kind]
  (xml/xselect item-loc "contributors" "organization" [:= "contributor_role" kind]))

;; --------------------------------------------------------------
;; Institutions

(defn parse-department [department-loc]
  (when-let [department (xml/xselect1 department-loc :text)]
    {:type :org
     :subtype :department
     :name department}))

(defn parse-departments [institution-loc]
  (map parse-department (xml/xselect institution-loc "institution_department")))

;; todo acronym and location {0, 6}
(defn parse-institution [institution-loc]
  (-> {:type :org
       :name (xml/xselect1 institution-loc "institution_name" :text)
       :acronym (xml/xselect1 institution-loc "institution_acronym" :text)
       :location (xml/xselect1 institution-loc "institution_location" :text)}
      (parse-attach :component institution-loc :multi parse-departments)))

(defn parse-institutions [parent-loc]
  (map parse-institution (xml/xselect parent-loc "institution")))

;; ---------------------------------------------------------------
;; Generic item parsing

(defn parse-item-ids
  "Extracts all IDs for an item (DOI, ISSN, ISBN, so on). Extracts into
   maps with the keys :type, :subtype, :value and :original."
  [item-loc]
  (-> []
      (if-conj (parse-doi item-loc))
      (concat (map parse-issn (find-issns item-loc)))
      (concat (map parse-isbn (find-isbns item-loc)))))

(defn parse-item-id-uris
  [item-loc]
  (-> []
      (if-conj (parse-doi-uri item-loc))
      (concat (map parse-supplementary-id-uri (find-supplementary-ids item-loc)))
      (concat (map parse-issn-uri (find-issns item-loc)))
      (concat (map parse-isbn-uri (find-isbns item-loc)))))

;; todo handle locations as first class items?
(defn parse-item-publisher [parent-loc]
  (when-let [publisher-loc (xml/xselect1 parent-loc "publisher")]
    {:type :org
     :name (xml/xselect1 publisher-loc "publisher_name" :text)
     :location (xml/xselect1 publisher-loc "publisher_place" :text)}))

(defn parse-item-citations [item-loc]
  (map parse-citation (find-citations item-loc)))

(defn parse-item-pub-dates [kind item-loc]
  (map parse-date (find-pub-dates item-loc kind)))

(defn parse-item-approval-dates [kind item-loc]
  (map parse-date (find-approval-dates item-loc kind)))

(defn parse-item-titles [item-loc]
  (-> []
      (concat (parse-full-titles item-loc))
      (concat (parse-abbrev-titles item-loc))
      (if-conj (parse-proceedings-title item-loc))
      (if-conj (parse-title item-loc))
      (if-conj (parse-subtitle item-loc))
      (if-conj (parse-language-title item-loc))))

(defn parse-item-contributors [kind item-loc]
  (-> []
      (concat (map parse-person-name (find-person-names item-loc kind)))
      (concat (map parse-organization (find-organizations item-loc kind)))))

(defn parse-grant [funding-identifier-loc]
  (-> {:type :grant}
      (attach-id (xml/xselect1 funding-identifier-loc :plain))))

(defn parse-grants [funder-group-loc]
  (map parse-grant
       (concat
        (xml/xselect funder-group-loc "assertion" [:= "name" "funding_identifier"])
        (xml/xselect funder-group-loc "assertion" [:= "name" "award_number"]))))

(defn normalize-funder-id-val [funder-id-val]
  (when funder-id-val
    (if-let [id-only (re-find #"\A\d+\Z" (.trim funder-id-val))]
      (fundref/id-to-doi-uri id-only)
      (to-long-doi-uri funder-id-val))))

(defn parse-funder [funder-group-loc]
  (let [funder-id-val (xml/xselect1 funder-group-loc 
                                    "assertion" 
                                    "assertion" 
                                    [:= "name" "funder_identifier"] 
                                    :plain)
        funder-uri (normalize-funder-id-val funder-id-val)]
    (-> {:type :org
         :name (xml/xselect1 funder-group-loc 
                             "assertion" 
                             [:= "name" "funder_name"] 
                             :plain)}
        (parse-attach :awarded funder-group-loc :multi parse-grants)
        (?> funder-uri attach-id funder-uri))))

(defn parse-single-funder [item-loc]
  (let [program-loc (xml/xselect item-loc "program")
        single-funder (parse-funder program-loc)]
    (if (or (:name single-funder) (not (empty? (:id single-funder))))
      [single-funder]
      [])))

(defn parse-item-funders [item-loc]
  (let [funder-groups-loc (concat 
                           (xml/xselect item-loc 
                                        "program" 
                                        ;[:= "name" "fundref"]
                                        "assertion" 
                                        [:= "name" "fundgroup"])
                           (xml/xselect item-loc
                                        "crossmark" 
                                        "custom_metadata" 
                                        "program" 
                                        [:= "name" "fundref"]
                                        "assertion"
                                        [:= "name" "fundgroup"]))]
    (concat
     (parse-single-funder item-loc)
     (map parse-funder funder-groups-loc))))

(def license-date-formatter (ftime/formatter "yyyy-MM-dd"))

(defn parse-license-start-date [license-loc]
  (if-let [raw-date (xml/xselect1 license-loc ["start_date"])]
    (let [d (ftime/parse license-date-formatter raw-date)]
      {:year (t/year d)
       :month (t/month d)
       :day (t/day d)})))

(defn parse-license 
  "Returns a license. :content-version can be any of tdm, vor, am or unspecified."
  [license-loc]
  (-> {:type :url
       :content-version (or (xml/xselect1 license-loc ["applies_to"]) "unspecified")
       :value (xml/xselect1 license-loc :text)}
      (parse-attach :start license-loc :single parse-license-start-date)))

(defn parse-item-licenses [item-loc]
  (let [license-locs (xml/xselect item-loc :> "license_ref")]
    (map parse-license license-locs)))

(declare parse-item)

(defn parse-component [component-loc]
  (-> (parse-item component-loc)
      (conj
       {:subtype :component
        :format (xml/xselect1 component-loc "format" :text)
        :format-mime (xml/xselect1 component-loc "format" ["mime_type"] :text)
        :size (xml/xselect1 component-loc ["component_size"] :text)
        :agency (xml/xselect1 component-loc ["reg-agency"] :text)
        :relation (xml/xselect1 component-loc ["parent_relation"] :text)
        :description (xml/xselect1 component-loc "description" :text)})))

(defn parse-component-list [parent-loc]
  (map parse-component (xml/xselect parent-loc "component_list" "component")))

(defn parse-item
  "Pulls out metadata that is somewhat standard across types: contributors,
   resource urls, some dates, titles, ids, citations, components, crossmark assertions
   and programs."
  [item-loc]
  (-> {:type :work}
      (attach-ids (parse-item-id-uris item-loc))
      (parse-attach :component item-loc :multi parse-component-list)
      (parse-attach :institution item-loc :multi parse-institutions)
      (parse-attach :funder item-loc :multi parse-item-funders)
      (parse-attach :author item-loc :multi (partial parse-item-contributors "author"))
      (parse-attach :editor item-loc :multi (partial parse-item-contributors "editor"))
      (parse-attach :translator item-loc :multi (partial parse-item-contributors "translator"))
      (parse-attach :chair item-loc :multi (partial parse-item-contributors "chair"))
      (parse-attach :publisher item-loc :single parse-item-publisher)
      (parse-attach :resource-resolution item-loc :single parse-resource)
      (parse-attach :resource-fulltext item-loc :multi (partial parse-collection "text-mining"))
      (parse-attach :resource-fulltext item-loc :multi (partial parse-collection "crawler"))
      (parse-attach :license item-loc :multi parse-item-licenses)
      (parse-attach :title item-loc :multi parse-item-titles)
      (parse-attach :citation item-loc :multi parse-item-citations)
      (parse-attach :published-print item-loc :multi (partial parse-item-pub-dates "print"))
      (parse-attach :published-online item-loc :multi (partial parse-item-pub-dates "online"))
      (parse-attach :approved-print item-loc :multi (partial parse-item-approval-dates "print"))
      (parse-attach :approved-online item-loc :multi (partial parse-item-approval-dates "online"))))

;; -----------------------------------------------------------------
;; Specific item parsing

(defn parse-journal-article [article-loc]
  (when article-loc
    (conj (parse-item article-loc)
          {:subtype :journal-article
           :first-page (xml/xselect1 article-loc "pages" "first_page" :text)
           :last-page (xml/xselect1 article-loc "pages" "last_page" :text)
           :other-pages (xml/xselect1 article-loc "pages" "other_pages" :text)})))

(defn parse-journal-issue [issue-loc]
  (when issue-loc
    (-> (parse-item issue-loc)
        (conj {:subtype :journal-issue
               :numbering (xml/xselect1 issue-loc "special_numbering" :text)
               :issue (xml/xselect1 issue-loc "issue" :text)}))))

(defn parse-journal-volume [volume-loc]
  (when volume-loc
    (-> (parse-item volume-loc)
        (conj {:subtype :journal-volume
               :volume (xml/xselect1 volume-loc "volume" :text)}))))

(defn parse-journal [journal-loc]
  (when journal-loc
    (let [issue (-> journal-loc (find-journal-issue) (parse-journal-issue))
          volume (-> journal-loc (find-journal-volume) (parse-journal-volume))
          article (-> journal-loc (find-journal-article) (parse-journal-article))
          journal (-> journal-loc (find-journal-metadata) (parse-item)
                      (assoc :subtype :journal))]
      (cond 
       (nil? issue)   (->> article
                           (attach-rel journal :component))
       (nil? volume)  (->> article
                           (attach-rel issue :component) 
                           (attach-rel journal :component))
       :else          (->> article
                           (attach-rel volume :component)
                           (attach-rel issue :component)
                           (attach-rel journal :component))))))

(defn parse-conf-paper [conf-paper-loc]
  (when conf-paper-loc
    (-> (parse-item conf-paper-loc)
        (conj
         {:subtype :proceedings-article
          :start-page (xml/xselect1 conf-paper-loc "pages" "start_page")
          :end-page (xml/xselect1 conf-paper-loc "pages" "end_page")
          :other-pages (xml/xselect1 conf-paper-loc "pages" "other_pages")}))))

;; todo perhaps make sponsor organisation and event location address first class items.
(defn parse-event [event-loc]
  (when event-loc
    (-> (parse-item event-loc)
        (parse-attach :start event-loc :single (comp parse-start-date find-event-date))
        (parse-attach :end event-loc :single (comp parse-end-date find-event-date))
        (conj
         {:type :event
          :subtype :conference
          :name (xml/xselect1 event-loc "conference_name" :text)
          :theme (xml/xselect1 event-loc "conference_theme" :text)
          :location (xml/xselect1 event-loc "conference_location" :text)
          :sponsor (xml/xselect1 event-loc "conference_sponsor" :text)
          :acronym (xml/xselect1 event-loc "conference_acronym" :text)
          :number (xml/xselect1 event-loc "conference_number" :text)}))))

(defn parse-conf [conf-loc]
  (when conf-loc
    (let [series-loc (xml/xselect1 conf-loc :> "proceedings_series_metadata")
          single-loc (xml/xselect1 conf-loc :> "proceedings_metadata")
          proceedings-loc (or series-loc single-loc)]
      (-> (parse-item proceedings-loc)
          (parse-attach :about conf-loc :single (comp parse-event find-event))
          (parse-attach :component conf-loc :single (comp parse-conf-paper find-conf-paper))
          (conj
           {:subtype (if series-loc :proceedings-series :proceedings)
            :coden (xml/xselect1 conf-loc :> "coden" :text)
            :subject (xml/xselect1 conf-loc :> "proceedings_subject")
            :volume (xml/xselect1 conf-loc :> "volume")})))))

(defn parse-content-item-type [content-item-loc]
  (let [type (xml/xselect1 content-item-loc ["component_type"])]
    (cond
     (= type "chapter") :chapter
     (= type "section") :section
     (= type "part") :part
     (= type "track") :track
     (= type "reference_entry") :reference-entry
     (= type "other") :other
     :else :other)))

(defn parse-content-item [content-item-loc]
  (when content-item-loc
    (-> (parse-item content-item-loc)
        (conj
         {:subtype (parse-content-item-type content-item-loc)
          :language (xml/xselect1 content-item-loc ["language"])
          :nest-level (xml/xselect1 content-item-loc ["level_sequence_number"])
          :component-number (xml/xselect1 content-item-loc "component_number" :text)
          :first-page (xml/xselect1 content-item-loc "pages" "first_page" :text)
          :last-page (xml/xselect1 content-item-loc "pages" "last_page" :text)
          :other-pages (xml/xselect1 content-item-loc "pages" "other_pages" :text)}))))

(defn parse-content-items [parent-loc]
  (map parse-content-item (xml/xselect parent-loc "content_item")))

(defn parse-single-book* [book-meta-loc content-item-loc book-type]
  (-> (parse-item book-meta-loc)
      (parse-attach :component content-item-loc :single parse-content-item)
      (conj
       {:subtype book-type
        :language (xml/xselect1 book-meta-loc ["language"])
        :edition-number (xml/xselect1 book-meta-loc "edition_number" :text)
        :volume (xml/xselect1 book-meta-loc "volume" :text)})))

(defn parse-single-book [book-meta-loc content-item-loc book-type]
  (if-let [series-meta-loc (xml/xselect1 book-meta-loc "series_metadata")]
    (-> (parse-item series-meta-loc)
        (parse-attach :component book-meta-loc 
                      :single #(parse-single-book* % content-item-loc book-type))
        (conj {:subtype :book-series}))
    (parse-single-book* book-meta-loc content-item-loc book-type)))

(defn parse-book-set [book-set-meta-loc content-item-loc book-type]
  (-> (parse-item (xml/xselect1 book-set-meta-loc "set_metadata"))
      (parse-attach 
       :component book-set-meta-loc 
       :single #(parse-single-book % content-item-loc book-type))
      (conj
       {:subtype :book-set
        :part-number (xml/xselect1 book-set-meta-loc "set_metadata" "part_number" :text)})))

(defn parse-book-series [book-series-meta-loc content-item-loc book-type]
  (-> (parse-item (xml/xselect1 book-series-meta-loc "series_metadata"))
      (parse-attach 
       :component book-series-meta-loc 
       :single #(parse-single-book % content-item-loc book-type))
      (conj
       {:subtype :book-series
        :coden (xml/xselect1 book-series-meta-loc "series_metadata" "coden" :text)
        :series-number (xml/xselect1 book-series-meta-loc "series_metadata" "series_number" :text)})))

(defn parse-book-type [book-loc]
  (let [type (xml/xselect1 book-loc ["book_type"])]
    (cond
     (= type "edited_book") :edited-book
     (= type "monograph") :monograph
     (= type "reference") :reference-book
     (= type "other") :book
     :else :book)))

(defn parse-book [book-loc]
  (let [book-meta-loc (xml/xselect1 book-loc "book_metadata")
        book-series-meta-loc (xml/xselect1 book-loc "book_series_metadata")
        book-set-meta-loc (xml/xselect1 book-loc "book_set_metadata")
        content-item-loc (xml/xselect1 book-loc "content_item")
        book-type (parse-book-type book-loc)]
    (cond
     book-meta-loc (parse-single-book book-meta-loc content-item-loc book-type)
     book-series-meta-loc (parse-book-series book-series-meta-loc content-item-loc book-type)
     book-set-meta-loc (parse-book-set book-set-meta-loc content-item-loc book-type))))

(defn parse-dataset-type [dataset-loc]
  (let [type (xml/xselect1 dataset-loc ["dataset_type"])]
    (cond
     (= type "record") :record
     (= type "collection") :collection
     (= type "crossmark_policy") :crossmark-policy
     (= type "other") :other
     :else :record)))

;; todo dates
(defn parse-dataset [dataset-loc]
  (-> (parse-item dataset-loc)
      (conj
       {:subtype :dataset
        :kind (parse-dataset-type dataset-loc)
        :format (xml/xselect1 dataset-loc "format" :text)
        :format-mime (xml/xselect1 dataset-loc "format" ["mime_type"] :text)
        :description (xml/xselect1 dataset-loc "description")})))

(defn parse-datasets [database-loc]
  (map parse-dataset (xml/xselect database-loc "dataset")))

;; todo institutions dates
(defn parse-database [database-loc]
  (when database-loc
    (let [metadata-loc (xml/xselect1 database-loc "database_metadata")]
      (-> (parse-item metadata-loc)
          (parse-attach :component database-loc :multi parse-datasets)
          (conj
           {:subtype :dataset
            :language (xml/xselect1 metadata-loc ["language"] :text)
            :description (xml/xselect1 metadata-loc "description")})))))

(defn parse-report-contract-number [report-loc]
  (xml/xselect1 report-loc "report-paper_metadata" "contact_number" :text))

(defn parse-single-report [report-loc]
  (-> (parse-item (xml/xselect1 report-loc "report-paper_metadata"))
      (parse-attach :component report-loc :multi parse-content-items)
      (conj 
       {:subtype :report
        :contract-number (parse-report-contract-number report-loc)})))

(defn parse-report-series* [series-meta-loc]
  (-> (parse-item series-meta-loc)
      (conj
       {:subtype :report-series
        :coden (xml/xselect1 series-meta-loc "coden" :text)
        :series-number (xml/xselect1 series-meta-loc "series_number" :text)})))  

(defn parse-report-series [series-meta-loc]
  (if-let [series-loc (xml/xselect1 series-meta-loc "series_metadata")]
    (-> (parse-item series-loc)
        (parse-attach :component series-meta-loc :single parse-report-series*)
        (conj
         {:subtype :report-series}))
    (parse-report-series* series-meta-loc)))

(defn parse-single-report-with-series [report-loc series-meta-loc]
  (-> (parse-report-series series-meta-loc)
      (parse-attach :component report-loc :single parse-single-report)))

;; todo parse institution
(defn parse-report [report-loc]
  (let [report-meta-loc (xml/xselect1 report-loc "report-paper_metadata")
        report-series-meta-loc (xml/xselect1 report-loc "report-paper_series_metadata")
        series-meta-loc (xml/xselect1 report-loc "report-paper_metadata" "series_metadata")]
    (cond
     series-meta-loc
     (parse-single-report-with-series report-loc series-meta-loc)
     report-meta-loc
     (parse-single-report report-loc)
     report-series-meta-loc
     (parse-report-series report-series-meta-loc))))

;; todo parse instituion
(defn parse-single-standard [standard-loc]
  (let [standard-metadata-loc (xml/xselect1 standard-loc "standard_metadata")]
    (-> (parse-item standard-metadata-loc)
        (parse-attach :component standard-loc :multi parse-content-items)
        (conj
         {:subtype :standard
          :volume (xml/xselect1 standard-metadata-loc "volume" :text)
          :edition-number (xml/xselect1 standard-metadata-loc "edition_number" :text)}))))

(defn parse-standard-series [series-meta-loc]
  (-> (parse-item series-meta-loc)
      (conj
       {:subtype :standard-series
        :coden (xml/xselect1 series-meta-loc "coden" :text)
        :series-number (xml/xselect1 series-meta-loc "series_number" :text)})))

(defn parse-single-standard-with-series [standard-loc series-meta-loc]
  (-> (parse-standard-series series-meta-loc)
      (parse-attach :component standard-loc :single parse-single-standard)))

(defn parse-standard [standard-loc]
  (let [standard-meta-loc (xml/xselect1 standard-loc "standard_metadata")
        standard-series-meta-loc (xml/xselect1 standard-loc "standard_series_metadata")
        series-meta-loc (xml/xselect1 standard-loc "standard_metadata" "series_metadata")]
    (cond
     series-meta-loc
     (parse-single-standard-with-series standard-loc series-meta-loc)
     standard-meta-loc
     (parse-single-standard standard-loc)
     standard-series-meta-loc
     (parse-standard-series standard-series-meta-loc))))

(defn parse-dissertation [dissertation-loc]
  (when dissertation-loc
    (let [person-loc (xml/xselect dissertation-loc "person_name")]
      (-> (parse-item dissertation-loc)
          (parse-attach :author person-loc :single parse-person-name)
          (conj
           {:subtype :dissertation
            :language (xml/xselect1 dissertation-loc ["language"] :text)
            :degree (xml/xselect1 dissertation-loc "degree" :text)})))))

(defn parse-stand-alone-component [sa-component-loc]
  (when sa-component-loc
    (let [id (xml/xselect1 sa-component-loc ["parent_doi"] :text)
          parent-doi (to-long-doi-uri id)]
      (-> {:type :work}
          (attach-id parent-doi)
          (parse-attach :component sa-component-loc :multi parse-component-list)))))

;; ---------------------------------------------------------------

(defn parse-primary-id [record]
  (-> record
      (xml/xselect1 "header" "identifier" :text)
      (to-long-doi-uri)))

(def oai-deposit-date-formatter (ftime/formatter "yyyy-MM-dd"))
(def openurl-deposit-date-formatter (ftime/formatter "yyyy-MM-dd HH:mm:ss"))

(defn parse-oai-deposit-date [loc]
  (when-let [date-text (xml/xselect1 loc "header" "datestamp" :text)]
    (ftime/parse oai-deposit-date-formatter date-text)))

(defn parse-openurl-deposit-date [loc]
  (when-let [date-text (xml/xselect1 loc ["timestamp"])]
    (ftime/parse openurl-deposit-date-formatter date-text)))

(defn parse-deposit-date [record]
  (when-let [d (or (parse-oai-deposit-date record)
                   (parse-openurl-deposit-date record))]
    {:type :date
     :day (t/day d)
     :month (t/month d)
     :year (t/year d)}))

(defn unixref-citation-parser
  "Produces lists of citations found in unixref. (Does not return item record structures.)"
  [oai-record]
  (map parse-citation (xml/xselect oai-record :> "citation_list" "citation")))

(defn unixref-record-parser
  "Returns a tree of the items present in an OAI record. Each item has 
   :contributor, :citation and :component keys that may list further items.

   Items can be any of:

   work
     journal
     journal-issue
     journal-volume
     journal-article
     proceedings
     proceedings-article
     report
     report-series
     standard
     dataset
     edited-book
     monograph
     reference-book
     book
     book-series
     book-set
     chapter
     section
     part
     track
     reference-entry
     other
   event
     conference
   citation
   person
   org
   date
   title
     short
     long
     secondary
   grant
   license

   Relations include:

   published-online
   published-print
   deposited
   start
   end
   citation
   author
   chair
   translator
   editor
   contributor
   about
   title
   resource-fulltext
   resource-resolution
   affiliation
   publisher
   component
   grant
   funder

   Each item may have a list of :id structures, a list of :title structures,
   a list of :date structures, any number of flat :key value pairs, and finally, 
   relationship structures :rel, which contains a key per relation type. Some
   common relationship types include :author, :citation and :component.
   Anything mentioned as a value in :rel will be an item itself.

   The result of this function is a list, [primary-id, item-tree]."
  [oai-record]
  (let [work (or
              (parse-stand-alone-component (find-stand-alone-component oai-record))
              (parse-dissertation (find-dissertation oai-record))
              (parse-standard (find-standard oai-record))
              (parse-report (find-report oai-record))
              (parse-database (find-database oai-record))
              (parse-journal (find-journal oai-record))
              (parse-book (find-book oai-record))
              (parse-conf (find-conf oai-record)))]
    (if work
      [(parse-primary-id oai-record)
       (parse-attach work :deposited oai-record :single parse-deposit-date)]
      [(parse-primary-id oai-record) work])))

;(defmethod ->format-name "xml" :unixref)
;(defmethod ->format-name "unixref-xml" :unixref)
;(defmethod ->format-name "application/vnd.crossref.unixref+xml" :unixref)
