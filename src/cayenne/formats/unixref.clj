(ns cayenne.formats.unixref
  (:require [clj-time.format :as ftime]
            [clj-time.core :as t]
            [cayenne.xml :as xml]
            [cayenne.conf :as conf]
            [cayenne.ids :as ids]
            [cayenne.ids.fundref :as fundref]
            [cayenne.item-tree :as itree]
            [clojure.tools.trace :as trace]
            [taoensso.timbre :as timbre :refer [info error]]
            [cayenne.ids.ctn :refer [normalize-ctn]]
            [clojure.string :as string])
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
  (let [existing (or (get-in item [:rel relation]) [])
        related (parse-fn loc)]
    (cond 
     (= kind :single)
     (if (not (nil? related))
       (assoc-in item [:rel relation] (conj existing related))
       item)
     (= kind :multi)
     (let [non-nil-related (filter (complement nil?) related)]
       (if (and (not (nil? non-nil-related)) (not (empty? non-nil-related)))
         (assoc-in item [:rel relation] (concat existing non-nil-related))
         item)))))

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

(defn find-posted-content [record-loc]
  (xml/xselect1 record-loc :> "posted_content"))

(defn find-conf [record-loc]
  (xml/xselect1 record-loc :> "conference"))

(defn find-peer-review [record-loc]
  (xml/xselect1 record-loc :> "peer_review"))

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

(defn find-pub-dates 
  ([work-loc kind]
     (xml/xselect work-loc "publication_date" [:= "media_type" kind]))
  ([work-loc]
     (xml/xselect work-loc "publication_date" [:has-not "media_type"])))

(defn find-approval-dates [work-loc]
  (xml/xselect work-loc "approval_date"))

(defn find-event-date [event-loc]
  (xml/xselect1 event-loc "conference_date"))

(defn find-database-dates [metadata-loc]
  (xml/xselect metadata-loc "database_date" "publication_date"))

(defn find-database-created-dates [metadata-loc]
  (xml/xselect metadata-loc "database_date" "creation_date"))

(defn find-database-updated-dates [metadata-loc]
  (xml/xselect metadata-loc "database_date" "update_date"))

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
    (when (> (Integer/parseInt year-val) 0)
      {:type :date
       :day day-val 
       :month (parse-month month-val) 
       :year year-val
       :time-of-year (parse-time-of-year month-val)})))

(defn parse-start-date [start-date-loc]
  (let [month-val (xml/xselect1 start-date-loc ["start_month"])]
    {:type :date
     :day (xml/xselect1 start-date-loc ["start_day"])
     :month (parse-month month-val)
     :year (xml/xselect1 start-date-loc ["start_year"])
     :time-of-year (parse-time-of-year month-val)}))

(defn parse-end-date [end-date-loc]
  (let [month-val (xml/xselect1 end-date-loc ["end_month"])]
    {:type :date
     :day (xml/xselect1 end-date-loc ["end_day"])
     :month (parse-month month-val)
     :year (xml/xselect1 end-date-loc ["end_year"])
     :time-of-year (parse-time-of-year month-val)}))

;; --------------------------------------------------------------
;; Citations

(defn find-citations [work-loc]
  (xml/xselect work-loc "citation_list" "citation"))

(defn parse-citation [citation-loc]
  (-> {:key (xml/xselect1 citation-loc ["key"])
       :doi (xml/xselect1 citation-loc "doi" :text)
       :doi-asserted-by (or (xml/xselect1 citation-loc "doi" ["provider"]) "publisher")
       :issn (to-issn-uri (xml/xselect1 citation-loc "issn" :text))
       :issn-type (or (xml/xselect1 citation-loc "issn" ["media_type"]) "print")
       :journal-title (xml/xselect1 citation-loc "journal_title" :text)
       :author (xml/xselect1 citation-loc "author" :text)
       :volume (xml/xselect1 citation-loc "volume" :text)
       :issue (xml/xselect1 citation-loc "issue" :text)
       :first-page (xml/xselect1 citation-loc "first_page" :text)
       :year (xml/xselect1 citation-loc "cYear" :text)
       :isbn (to-isbn-uri (xml/xselect1 citation-loc "isbn" :text))
       :isbn-type (or (xml/xselect1 citation-loc "isbn" ["media_type"]) "print")
       :series-title (xml/xselect1 citation-loc "series_title" :text)
       :volume-title (xml/xselect1 citation-loc "volume_title" :text)
       :edition (xml/xselect1 citation-loc "edition_number" :text)
       :component (xml/xselect1 citation-loc "component_number" :text)
       :article-title (xml/xselect1 citation-loc "article_title" :text)
       :standard-designator (xml/xselect1 citation-loc "std_designator" :text)
       :standards-body (xml/xselect1 citation-loc "standards_body" :text)
       :unstructured (xml/xselect1 citation-loc "unstructured_citation" :text)}
      (attach-id (to-long-doi-uri (xml/xselect1 citation-loc "doi" :text)))))

(defn parse-citation-ids [citation-loc]
  {:doi (to-long-doi-uri (xml/xselect1 citation-loc "doi" :text))})

;; ---------------------------------------------------------------------
;; Assertions

(defn find-assertions [work-loc]
  (xml/xselect work-loc :> "custom_metadata" "assertion"))

(defn parse-assertion [assertion-loc]
  {:type :assertion
   :order (xml/xselect1 assertion-loc ["order"])
   :url (xml/xselect1 assertion-loc ["href"])
   :explanation-url (xml/xselect1 assertion-loc ["explanation"])
   :value (xml/xselect1 assertion-loc :text)
   :name (xml/xselect1 assertion-loc ["name"])
   :label (xml/xselect1 assertion-loc ["label"])
   :group-label (xml/xselect1 assertion-loc ["group_label"])
   :group-name (xml/xselect1 assertion-loc ["group_name"])})

;; ---------------------------------------------------------------------
;; Resources

(defn parse-collection-item 
  "Returns a resource link. :content-version can be any of tdm, vor, am."
  [intended-application coll-item-loc]
  {:type :url
   :content-version (or (xml/xselect1 coll-item-loc ["content_version"])
                        "vor")
   :intended-application intended-application
   :content-type (or (xml/xselect1 coll-item-loc ["mime_type"]) "unspecified")
   :value (xml/xselect1 coll-item-loc :text)})

(defn parse-collection
  ([with-attribute item-loc]
      (let [items (xml/xselect item-loc 
                            "doi_data" :> "collection"
                            [:= "property" with-attribute] "item"
                            "resource")]
     (map (partial parse-collection-item with-attribute) items)))
  ([with-attribute with-crawler with-application item-loc]
   (let [items (xml/xselect item-loc 
                            "doi_data" :> "collection"
                            [:= "property" with-attribute] "item"
                            [:= "crawler" with-crawler]
                            "resource")]
     (map (partial parse-collection-item with-application) items))))

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

(defn parse-group-title [item-loc]
  (when-let [title (xml/xselect1 item-loc "group_title" :text)]
    {:type :title :subtype :group :value title}))

(defn parse-title [item-loc]
  (when-let [title (xml/xselect1 item-loc "titles" "title" :text)]
    {:type :title :subtype :long :value title}))

(defn parse-subtitle [item-loc]
  (if-let [title (xml/xselect1 item-loc "titles" "subtitle" :text)]
    {:type :title :subtype :secondary :value title}))

(defn parse-language-title [item-loc]
  (if-let [title-loc (xml/xselect1 item-loc "titles" "original_language_title")]
    {:type :title
     :subtype :original
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

(defn find-item-numbers [item-loc]
  (xml/xselect item-loc "publisher_item" "item_number"))

;; ---------------------------------------------------------------
;; Contributors

(defn parse-orcid [person-loc]
  (when-let [orcid-loc (xml/xselect1 person-loc "ORCID")]
    {:type :id
     :subtype :orcid
     :authenticated (-> (xml/xselect1 orcid-loc ["authenticated"])
                        (or "false")
                        (Boolean/parseBoolean))
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
  (let [orcid (parse-orcid person-loc)
        person {:type :person 
                :first-name (xml/xselect1 person-loc "given_name" :text)
                :last-name (xml/xselect1 person-loc "surname" :text)
                :sequence (or (xml/xselect1 person-loc ["sequence"]) "additional")
                :suffix (xml/xselect1 person-loc "suffix" :text)}
        parse-fn #(map parse-affiliation (find-affiliations %))
        person-with-affils (-> person
                               (parse-attach :affiliation person-loc :multi parse-fn))]
    (if orcid
      (-> person-with-affils
          (assoc :orcid-authenticated (:authenticated orcid))
          (attach-id (:value orcid)))
      person-with-affils)))

(defn parse-organization [org-loc]
  {:type :org
   :sequence (or (xml/xselect1 org-loc ["sequence"]) "additional")
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
       :location (or (xml/xselect1 institution-loc "institution_place" :text)
                     (xml/xselect1 institution-loc "institution_location" :text))}
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

(defn parse-item-assertions [item-loc]
  (map parse-assertion (find-assertions item-loc)))

(defn parse-item-number [item-number-loc]
  {:type :number
   :kind (xml/xselect1 item-number-loc ["item_number_type"])
   :value (xml/xselect1 item-number-loc :text)})

(defn parse-item-numbers [item-loc]
  (map parse-item-number (find-item-numbers item-loc)))

(defn parse-item-pub-dates 
  ([kind item-loc]
     (map parse-date (find-pub-dates item-loc kind)))
  ([item-loc]
     (map parse-date (find-pub-dates item-loc))))

(defn parse-database-pub-dates [item-loc]
  (map parse-date (find-database-dates item-loc)))

(defn parse-database-created-dates [item-loc]
  (map parse-date (find-database-created-dates item-loc)))

(defn parse-database-updated-dates [item-loc]
  (map parse-date (find-database-updated-dates item-loc)))

(defn parse-item-approval-dates [item-loc]
  (map parse-date (find-approval-dates item-loc)))

(defn parse-item-posted-date [item-loc]
  (when-let [posted-date-loc (xml/xselect1 item-loc "posted_date")]
    (parse-date posted-date-loc)))

(defn parse-item-accepted-date [item-loc]
  (when-let [accepted-date-loc (xml/xselect1 item-loc "acceptance_date")]
    (parse-date accepted-date-loc)))

(defn parse-item-titles [item-loc]
  (-> []
      (concat (parse-full-titles item-loc))
      (concat (parse-abbrev-titles item-loc))
      (if-conj (parse-group-title item-loc))
      (if-conj (parse-proceedings-title item-loc))
      (if-conj (parse-title item-loc))
      (if-conj (parse-subtitle item-loc))
      (if-conj (parse-language-title item-loc))))

(defn parse-item-contributors [kind item-loc]
  (let [by-sequence
        (-> []
            (concat (map parse-person-name (find-person-names item-loc kind)))
            (concat (map parse-organization (find-organizations item-loc kind)))
            ((partial group-by :sequence)))]
    (concat
     (get by-sequence "first") (get by-sequence "additional"))))

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
  (let [funder-id-loc (or
                       (xml/xselect1 funder-group-loc
                                     [:= "name" "funder_identifier"])
                       (xml/xselect1 funder-group-loc 
                                     "assertion" 
                                     [:= "name" "funder_identifier"])
                       (xml/xselect1 funder-group-loc
                                     "assertion"
                                     [:= "name" "funder_name"]
                                     "assertion"
                                     [:= "name" "funder_identifier"]))
        funder-id-val (xml/xselect1 funder-id-loc :plain)
        funder-id-source (or (xml/xselect1 funder-id-loc ["provider"]) "publisher")
        funder-name-val (or
                         (xml/xselect1 funder-group-loc
                                       [:= "name" "funder_name"]
                                       :plain)
                         (xml/xselect1 funder-group-loc
                                       "assertion"
                                       [:= "name" "funder_name"]
                                       :plain))
        funder-uri (normalize-funder-id-val funder-id-val)]
    (-> {:type :org :name funder-name-val}
        (parse-attach :awarded funder-group-loc :multi parse-grants)
        (?> (and funder-uri funder-id-source) assoc :doi-asserted-by funder-id-source)
        (?> funder-uri attach-id funder-uri))))

(defn parse-item-funders-without-fundgroup [item-loc]
  (let [funder-group-locs (concat
                           (xml/xselect item-loc "program")
                           (xml/xselect item-loc "crossmark" "custom_metadata" "program"))]
    (->> funder-group-locs
         (filter #(or (xml/xselect1 % "assertion" [:= "name" "funder_name"])
                      (xml/xselect1 % "assertion" [:= "name" "funder_identifier"])))
         (map parse-funder))))      

(defn parse-item-funders-with-fundgroup [item-loc]
  (let [funder-groups-loc (concat 
                           (xml/xselect item-loc 
                                        "program" 
                                        "assertion" 
                                        [:= "name" "fundgroup"])
                           (xml/xselect item-loc
                                        "crossmark" 
                                        "custom_metadata" 
                                        "program" 
                                        "assertion"
                                        [:= "name" "fundgroup"]))]
    (map parse-funder funder-groups-loc)))

(defn parse-item-funders [item-loc]
  (concat
   (parse-item-funders-without-fundgroup item-loc)
   (parse-item-funders-with-fundgroup item-loc)))

(defn parse-clinical-trial-number [clinical-trial-number-loc]
  (let [registry-id (xml/xselect1 clinical-trial-number-loc ["registry" :plain])
        ctn-type (xml/xselect1 clinical-trial-number-loc ["type" :plain])
        ctn-val (normalize-ctn (xml/xselect1 clinical-trial-number-loc :plain))]
    {:type :ctn :registry registry-id :ctn-type ctn-type :ctn ctn-val}))

(defn parse-item-clinical-trial-numbers
  [item-loc]
  (let [ctns (concat 
               (xml/xselect item-loc 
                            "crossmark" 
                            "custom_metadata" 
                            "program" 
                            "clinical-trial-number")
               (xml/xselect item-loc
                            "program"
                            "clinical-trial-number"))
      parsed (map parse-clinical-trial-number ctns)]
  parsed))

(def license-date-formatter (ftime/formatter "yyyy-MM-dd"))

;; some publishers are depositing dates as a date time in an unknown
;; format. temporarily get around that by taking the first 10 chars -
;; what should be the yyyy-MM-dd date.

(defn parse-license-start-date [license-loc]
  (if-let [raw-date (xml/xselect1 license-loc ["start_date"])]
    (let [concat-date (apply str (take 10 raw-date))
          d (ftime/parse license-date-formatter concat-date)]
      {:type :date
       :year (t/year d)
       :month (t/month d)
       :day (t/day d)})))

(defn parse-license 
  "Returns a license. :content-version can be any of tdm, vor, am or unspecified."
  [license-loc]
  (-> {:type :url
       :content-version (or (xml/xselect1 license-loc ["applies_to"]) "unspecified")
       :value (string/trim (xml/xselect1 license-loc :text))}
      (parse-attach :start license-loc :single parse-license-start-date)))

(defn parse-item-licenses [item-loc]
  (let [license-locs (xml/xselect item-loc :> "license_ref")]
    (map parse-license license-locs)))

(defn parse-archive
  [archive-loc]
  {:type :org
   :subtype :archive
   :name (xml/xselect1 archive-loc ["name"])})

(defn parse-item-archives [item-loc]
  (let [archive-locs (xml/xselect item-loc :> "archive_locations" "archive")]
    (map parse-archive archive-locs)))

(defn parse-update-policy [item-loc]
  (when-let [policy-doi (xml/xselect1 item-loc :> "crossmark" "crossmark_policy" :text)]
    {:type :id
     :subtype :doi
     :value (to-long-doi-uri policy-doi)
     :original policy-doi}))

(defn parse-domain-exclusive-status [item-loc]
  (when-let [exclusive (xml/xselect1 item-loc :> "crossmark" "crossmark_domain_exclusive" :text)]
    (= (string/lower-case exclusive) "true")))

(defn parse-domains [item-loc]
  (let [domains (xml/xselect item-loc :> "crossmark" "crossmark_domains" "crossmark_domain")]
    (map #(string/trim (xml/xselect1 % :text)) domains)))

(defn parse-resource-domain [item-loc]
  (when-let [domain (xml/xselect1 item-loc "doi_data" "resource" :text)]
    (last (re-find #"//(.+?)/" domain))))

(def update-date-formatter (ftime/formatter "yyyy-MM-dd"))

(defn parse-update-date [update-loc]
  (let [update-date (ftime/parse update-date-formatter
                                 (xml/xselect1 update-loc ["date"]))]
    {:type :date
     :day (t/day update-date)
     :month (t/month update-date)
     :year (t/year update-date)}))

(defn parse-update [update-loc]
  (let [update-to-doi (xml/xselect1 update-loc :text)]
    (-> {:type :update
         :subtype (xml/xselect1 update-loc ["type"])
         :label (xml/xselect1 update-loc ["label"])
         :value (to-long-doi-uri update-to-doi)
         :original update-to-doi}
        (parse-attach :updated update-loc :single parse-update-date))))

(defn parse-updates [item-loc]
  (when-let [updates (xml/xselect item-loc :> "crossmark" "updates" "update")]
    (map parse-update updates)))

(defn parse-relation [relation-loc]
  (let [relation-detail-loc (or (xml/xselect1 relation-loc "inter_work_relation")
                                (xml/xselect1 relation-loc "intra_work_relation"))]
    {:type :relation
     :subtype (-> (xml/xselect1 relation-detail-loc ["relationship-type"])
                  (string/replace #"([A-Z])" "-$1")
                  string/lower-case
                  keyword)
     :claimed-by :subject
     :object-type (xml/xselect1 relation-detail-loc ["identifier-type"])
     :object-namespace (xml/xselect1 relation-detail-loc ["namespace"])
     :object (xml/xselect1 relation-detail-loc :text)}))

(defn parse-relations [item-loc]
  (when-let [relations (xml/xselect item-loc :> "related_item")]
    (map parse-relation relations)))

(defn parse-item-abstract [item-loc]
  (when-let [abstract-loc (xml/xselect1 item-loc :> "abstract")]
    {:type :abstract
     :plain (-> (xml/xselect1 abstract-loc :text) string/trim)
     :xml (-> (xml/xselect1 abstract-loc :xml) string/trim)}))

(defn parse-item-issn-details [item-loc]
  (map parse-issn (find-issns item-loc)))

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
  (let [parse-similarity-collection (partial parse-collection
                                             "crawler-based"
                                             "iParadigms"
                                             "similarity-checking")]
    (-> {:type :work}
        (attach-ids (parse-item-id-uris item-loc))
        (parse-attach :update-policy item-loc :single parse-update-policy)
        (parse-attach :domain-exclusive item-loc :single parse-domain-exclusive-status)
        (parse-attach :domains item-loc :multi parse-domains)
        (parse-attach :updates item-loc :multi parse-updates)
        (parse-attach :component item-loc :multi parse-component-list)
        (parse-attach :institution item-loc :multi parse-institutions)
        (parse-attach :funder item-loc :multi parse-item-funders)
        (parse-attach :clinical-trial-number item-loc :multi parse-item-clinical-trial-numbers)
        (parse-attach :author item-loc :multi (partial parse-item-contributors "author"))
        (parse-attach :editor item-loc :multi (partial parse-item-contributors "editor"))
        (parse-attach :translator item-loc :multi (partial parse-item-contributors "translator"))
        (parse-attach :chair item-loc :multi (partial parse-item-contributors "chair"))
        (parse-attach :publisher item-loc :single parse-item-publisher)
        (parse-attach :resource-resolution item-loc :single parse-resource)
        (parse-attach :resource-fulltext item-loc :multi (partial parse-collection "text-mining"))
        (parse-attach :resource-fulltext item-loc :multi (partial parse-collection "unspecified"))
        (parse-attach :resource-fulltext item-loc :multi (partial parse-collection "syndication"))
        (parse-attach :resource-fulltext item-loc :multi parse-similarity-collection)
        (parse-attach :relation item-loc :multi parse-relations)
        (parse-attach :license item-loc :multi parse-item-licenses)
        (parse-attach :archived-with item-loc :multi parse-item-archives)
        (parse-attach :title item-loc :multi parse-item-titles)
        (parse-attach :citation item-loc :multi parse-item-citations)
        (parse-attach :published-print item-loc :multi (partial parse-item-pub-dates "print"))
        (parse-attach :published-online item-loc :multi (partial parse-item-pub-dates "online"))
        (parse-attach :published-other item-loc :multi (partial parse-item-pub-dates "other"))
        (parse-attach :published item-loc :multi parse-item-pub-dates)
        (parse-attach :posted item-loc :single parse-item-posted-date)
        (parse-attach :accepted item-loc :single parse-item-accepted-date)
        (parse-attach :assertion item-loc :multi parse-item-assertions)
        (parse-attach :abstract item-loc :single parse-item-abstract)
        (parse-attach :number item-loc :multi parse-item-numbers)
        (parse-attach :issn item-loc :multi parse-item-issn-details)
        (parse-attach :approved item-loc :multi parse-item-approval-dates))))

;; crawler should be 'crawler-based' - but we may not want to include those anyway

;; -----------------------------------------------------------------
;; Specific item parsing

(defn parse-posted-content [item-loc]
  (when item-loc
    (conj (parse-item item-loc)
          {:subtype :posted-content})))

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

(defn parse-review [peer-review-loc]
  (let [running-number (xml/xselect1 peer-review-loc "running_number" :text)
        competing-interest-statement (xml/xselect1 peer-review-loc "competing_interest_statement" :text)
        revision-round (xml/xselect1 peer-review-loc ["revision-round"])
        stage (xml/xselect1 peer-review-loc ["stage"])
        recommendation (xml/xselect1 peer-review-loc ["recommendation"])
        r-type (xml/xselect1 peer-review-loc ["type"])
        language (xml/xselect1 peer-review-loc ["language"])]
    {:review {:running-number (string/trim running-number)
              :revision-round (string/trim revision-round)
              :stage (string/trim stage)
              :competing-interest-statement (string/trim competing-interest-statement)
              :recommendation (string/trim recommendation)
              :review-type (string/trim r-type)
              :language (string/trim language)}}))

(defn parse-peer-review [peer-review-loc]
  (when peer-review-loc
    (let [person-loc (-> (xml/xselect1 peer-review-loc "contributors")
                         (xml/xselect1 "person_name"))]
    (-> (parse-item peer-review-loc)
        (parse-attach :domains peer-review-loc :single parse-resource-domain)
        (parse-attach :author person-loc :single parse-person-name)
        (parse-attach :published (xml/xselect1 peer-review-loc "review_date") :single parse-date)
        (conj (parse-review peer-review-loc))
        (conj {:subtype :peer-review})))))

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
          :sponsor (xml/xselect event-loc "conference_sponsor" :text)
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
       :single #(parse-single-book* % content-item-loc book-type))
      (conj
       {:subtype :book-set
        :part-number (xml/xselect1 book-set-meta-loc "set_metadata" "part_number" :text)})))

(defn parse-book-series [book-series-meta-loc content-item-loc book-type]
  (-> (parse-item (xml/xselect1 book-series-meta-loc "series_metadata"))
      (parse-attach 
       :component book-series-meta-loc 
       :single #(parse-single-book* % content-item-loc book-type))
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
      (parse-attach :published dataset-loc :multi parse-database-pub-dates)
      (parse-attach :content-created dataset-loc :multi parse-database-created-dates)
      (parse-attach :content-updated dataset-loc :multi parse-database-updated-dates)
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
          (parse-attach :published metadata-loc :multi parse-database-pub-dates)
          (parse-attach :content-created metadata-loc :multi parse-database-created-dates)
          (parse-attach :content-updated metadata-loc :multi parse-database-updated-dates)
          (conj
           {:subtype :database
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

(defn parse-report-series* [report-loc series-meta-loc]
  (-> (parse-item series-meta-loc)
      (parse-attach :component report-loc :multi parse-content-items)
      (conj
       {:subtype :report-series
        :coden (xml/xselect1 series-meta-loc "coden" :text)
        :series-number (xml/xselect1 series-meta-loc "series_number" :text)})))

(defn parse-report-series [report-loc series-meta-loc]
  (if-let [series-loc (xml/xselect1 series-meta-loc "series_metadata")]
    (-> (parse-item series-loc)
        (parse-attach :component series-meta-loc :single #(parse-report-series* report-loc %))
        (conj
         {:subtype :report-series}))
    (parse-report-series* report-loc series-meta-loc)))

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
     (parse-report-series report-loc report-series-meta-loc))))

(defn parse-standards-body [standards-body-loc]
  (when standards-body-loc
    {:type :org
     :subtype :standards-body
     :name (xml/xselect1 standards-body-loc "standards_body_name" :text)
     :acronym (xml/xselect1 standards-body-loc "standards_body_acronym" :text)}))

(defn parse-single-standard [standard-loc]
  (let [standard-metadata-loc (xml/xselect1 standard-loc "standard_metadata")
        standards-body-loc (xml/xselect1 standard-metadata-loc "standards_body")]
    (-> (parse-item standard-metadata-loc)
        (parse-attach :component standard-loc :multi parse-content-items)
        (parse-attach :standards-body standards-body-loc :single parse-standards-body)
        (conj
         {:subtype :standard
          :volume (xml/xselect1 standard-metadata-loc "volume" :text)
          :edition-number (xml/xselect1 standard-metadata-loc "edition_number" :text)}))))

(defn parse-standard-series [standard-loc series-meta-loc]
  (-> (parse-item series-meta-loc)
      (parse-attach :component standard-loc :multi parse-content-items)
      (conj
       {:subtype :standard-series
        :coden (xml/xselect1 series-meta-loc "coden" :text)
        :series-number (xml/xselect1 series-meta-loc "series_number" :text)})))

(defn parse-single-standard-with-series [standard-loc series-meta-loc]
  (-> (parse-standard-series standard-loc series-meta-loc)
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
     (parse-standard-series standard-loc standard-series-meta-loc))))

(defn parse-degree [dissertation-loc]
  (map
   #(hash-map :value (xml/xselect1 % :text)
              :type :degree)
   (xml/xselect dissertation-loc "degree")))

(defn parse-dissertation [dissertation-loc]
  (when dissertation-loc
    (let [person-loc (xml/xselect dissertation-loc "person_name")]
      (-> (parse-item dissertation-loc)
          (parse-attach :author person-loc :single parse-person-name)
          (parse-attach :component dissertation-loc :multi parse-content-items)
          (parse-attach :degree dissertation-loc :multi parse-degree)
          (conj
           {:subtype :dissertation
            :language (xml/xselect1 dissertation-loc ["language"] :text)})))))

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
     standard-series
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
     dissertation
     other
   event
     conference
   citation
   peer-review
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
   first-deposited
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
   number
   grant
   funder
   archived-with

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
              (parse-posted-content (find-posted-content oai-record))
              (parse-book (find-book oai-record))
              (parse-conf (find-conf oai-record))
              (parse-peer-review (find-peer-review oai-record)))]
    (if work
      [(parse-primary-id oai-record)
       (parse-attach work :deposited oai-record :single parse-deposit-date)]
       [(parse-primary-id oai-record) work])))

;(defmethod ->format-name "xml" :unixref)
;(defmethod ->format-name "unixref-xml" :unixref)
;(defmethod ->format-name "application/vnd.crossref.unixref+xml" :unixref)
