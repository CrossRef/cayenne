(ns cayenne.formats.unixref
  (:require [clojure.stacktrace :as st])
  (:require [cayenne.xml :as xml])
  (:use [cayenne.ids.doi]))

;; -----------------------------------------------------------------
;; Helpers

;; todo rename to parse-attach-rel
(defn parse-attach [item relation loc kind parse-fn]
  "Attach a relation by running a function on an xml location."
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

(defn attach-rel [item relation related-item]
  "Attach related item to another item via relation."
  (let [existing (or (get item relation) [])]
    (assoc-in item [:rel relation] (conj existing related-item))))

(defn append-items [a b]
  "Appends a map, or concats a list of maps, to an existing item list."
  (if (map? b)
    (conj a b)
    (concat a b)))

(defn if-conj [coll item]
  "Conjion item to coll iff item is not nil."
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

(defn find-event [conf-loc]
  "Sometimes present in a proceedings. Never has a DOI."
  (xml/xselect1 conf-loc "event_metadata"))

(defn find-conf-paper [conf-loc]
  "Found in conferences."
  (xml/xselect1 conf-loc "conference_paper"))

(defn find-book [record-loc]
  ())

(defn find-book-series [record-loc]
  ())

(defn find-dissertation [record-loc]
  ())

(defn find-institution [record-loc]
  "Sometimes found in a dissertation."
  ())

(defn find-report-paper [record-loc]
  ())

(defn find-standard [record-loc]
  ())

(defn find-database [record-loc]
  ())

(defn find-stand-alone-component [record-loc]
  ())

(defn find-components [record-loc]
  "One of chapter, section, part, track, reference_entry or other."
  ())

;; --------------------------------------------------------------------
;; Dates

(defn find-pub-dates [work-loc kind]
  (xml/xselect work-loc "publication_date" [:= "media_type" kind]))

(defn find-event-date [event-loc]
  (xml/xselect1 event-loc "conference_date"))

(defn parse-month [month]
  (if (nil? month)
    nil
    (let [month-val (Integer/parseInt month)]
      (if (and (>= month-val 1) (<= month-val 12))
        month-val
        nil))))

(defn parse-time-of-year [month]
  (if (nil? month)
    nil
    (let [month-val (Integer/parseInt month)]
      (cond
       (= month-val 21) :spring
       (= month-val 22) :summer
       (= month-val 23) :autumn
       (= month-val 24) :winter
       (= month-val 31) :first-quarter
       (= month-val 32) :second-quarter
       (= month-val 33) :third-quarter
       (= month-val 34) :forth-quarter
       :else nil))))

(defn parse-pub-date [pub-date-loc]
  "Parse 'print' or 'online' publication dates."
  (let [day-val (xml/xselect1 pub-date-loc "day" :text)
        month-val (xml/xselect1 pub-date-loc "month" :text)
        year-val (xml/xselect1 pub-date-loc "year" :text)]
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
  {:doi (normalize-long-doi (xml/xselect1 citation-loc "doi" :text))
   :display-doi (xml/xselect1 citation-loc "doi" :text)
   :issn (xml/xselect1 citation-loc "issn" :text)
   :journal-title (xml/xselect1 citation-loc "journal_title" :text)
   :author (xml/xselect1 citation-loc "author" :text)
   :volume (xml/xselect1 citation-loc "volume" :text)
   :issue (xml/xselect1 citation-loc "issue" :text)
   :first-page (xml/xselect1 citation-loc "first_page" :text)
   :year (xml/xselect1 citation-loc "cYear" :text)
   :isbn (xml/xselect1 citation-loc "isbn" :text)
   :series-title (xml/xselect1 citation-loc "series_title" :text)
   :volume-title (xml/xselect1 citation-loc "volume_title" :text)
   :edition-number (xml/xselect1 citation-loc "edition_number" :text)
   :component-number (xml/xselect1 citation-loc "component_number" :text)
   :article-title (xml/xselect1 citation-loc "article_title" :text)
   :unstructured (xml/xselect1 citation-loc "unstructured_citation" :text)})

(defn parse-citation-ids [citation-loc]
  {:doi (xml/xselect1 citation-loc "doi" :text)})

;; ---------------------------------------------------------------------
;; Resources

(defn parse-collection-item [coll-item-loc]
  {:type :url
   :value (xml/xselect1 coll-item-loc "resource" :text)})

(defn parse-collection [with-attribute item-loc]
  (let [items (xml/xselect item-loc "doi_data" :> "item" [:has with-attribute])]
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
  (if-let [title (xml/xselect1 item-loc "titles" "title" :text)]
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

;; ---------------------------------------------------------------
;; Contributors

(defn parse-affiliation [affiliation-loc]
  (when affiliation-loc
    {:type :org
     :name (xml/xselect1 affiliation-loc :text)}))

(defn find-affiliation [person-loc]
  (xml/xselect1 person-loc "affiliation"))

(defn parse-person-name [person-loc]
  (let [person {:type :person
                :first-name (xml/xselect1 person-loc "given_name" :text)
                :last-name (xml/xselect1 person-loc "surname" :text)
                :suffix (xml/xselect1 person-loc "suffix" :text)}
        parse-fn (comp parse-affiliation find-affiliation)]
    (-> person
      (parse-attach :affiliation person-loc :single parse-fn))))

(defn parse-organization [org-loc]
  {:type :org
   :name (xml/xselect1 org-loc :text)})

(defn find-person-names [item-loc kind]
  (xml/xselect item-loc "contributors" "person_name" [:= "contributor_role" kind]))

(defn find-organizations [item-loc kind]
  (xml/xselect item-loc "contributors" "organization" [:= "contributor_role" kind]))

;; --------------------------------------------------------------
;; Identifiers

(defn parse-doi [item-loc]
  (if-let [doi (xml/xselect1 item-loc "doi_data" "doi" :text)]
    {:type :doi
     :subtype :crossref
     :value (normalize-long-doi doi)
     :original doi}))

(defn find-issns [item-loc]
  (xml/xselect item-loc "issn"))

;; todo normalize issn
(defn parse-issn [issn-loc]
  (let [issn-type (or (xml/xselect1 issn-loc ["media_type"]) "print")
        issn-value (xml/xselect1 issn-loc :text)]
    {:type :issn
     :subtype (if (= issn-type "print") :print :electronic)
     :value issn-value
     :original issn-value}))

(defn find-isbns [item-loc]
  (xml/xselect item-loc "isbn"))

;; todo normalize isbn
(defn parse-isbn [isbn-loc]
  (let [isbn-type (or (xml/xselect1 isbn-loc ["media_type"]) "print")
        isbn-value (xml/xselect1 isbn-loc :text)]
    {:type :isbn
     :subtype (if (= isbn-type "print") :print :electronic)
     :value isbn-value
     :original isbn-value}))


;; ---------------------------------------------------------------
;; Generic item parsing

(defn parse-item-ids [item-loc]
  "Extracts all IDs for an item (DOI, ISSN, ISBN, so on). Extracts into
   maps with the keys :type, :subtype, :value and :original."
  (-> []
      (if-conj (parse-doi item-loc))
      (concat (map parse-issn (find-issns item-loc)))
      (concat (map parse-isbn (find-isbns item-loc)))))

(defn parse-item-citations [item-loc]
  (map parse-citation (find-citations item-loc)))

(defn parse-item-pub-dates [kind item-loc]
  (map parse-pub-date (find-pub-dates item-loc kind)))

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

; also todo: publisher_item crossmark component_list
(defn parse-item [item-loc]
  "Pulls out metadata that is somewhat standard across types: contributors,
   resource urls, some dates, titles, ids, citations and components."
  (-> {:type :work}
      (parse-attach :author item-loc :multi (partial parse-item-contributors "author"))
      (parse-attach :editor item-loc :multi (partial parse-item-contributors "editor"))
      (parse-attach :translator item-loc :multi (partial parse-item-contributors "translator"))
      (parse-attach :chair item-loc :multi (partial parse-item-contributors "chair"))
      (parse-attach :id item-loc :multi parse-item-ids)
      (parse-attach :resource-resolution item-loc :single parse-resource)
      (parse-attach :resource-fulltext item-loc :multi (partial parse-collection "crawler"))
      (parse-attach :title item-loc :multi parse-item-titles)
      ;(parse-attach :citation item-loc :multi parse-item-citations)
      (parse-attach :published-print item-loc :multi (partial parse-item-pub-dates "print"))
      (parse-attach :published-online item-loc :multi (partial parse-item-pub-dates "online"))))

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
    (let [proceedings-loc (xml/xselect1 conf-loc "proceedings_metadata")]
      (-> (parse-item proceedings-loc)
          (parse-attach :component conf-loc :single (comp parse-event find-event))
          (parse-attach :component conf-loc :single (comp parse-conf-paper find-conf-paper))
          (conj
           {:subtype :proceedings
            :coden (xml/xselect1 proceedings-loc "coden" :text)
            :subject (xml/xselect1 proceedings-loc "proceedings_subject")
            :volume (xml/xselect1 proceedings-loc "volume")})))))

;(defn parse-book [book-loc]
;  (let [series-loc (xml/xselect1 book-loc "book_metadata" "series_metadata")
;        content-loc (xml/xselect1 book-loc "content_item")]
    

;; ---------------------------------------------------------------

(defn unixref-record-parser [oai-record]
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
     report-article
     standard
     dataset
   event
     conference
   citation
   person
   org
   date
   doi
     crossref
   issn
     print
     electronic
   isbn
     print
     electroic
   title
     short
     long
     secondary

   Each item may have a list of :id structures, a list of :title structures,
   a list of :date structures, any number of flat :key value pairs, and finally, 
   relationship structures :rel, which contains a key per relation type. Some
   common relationship types include :author, :citation and :component.
   Anything mentioned as a value in :rel will be an item itself.

   The result of this function is a list of trees (and in most cases the list will
   contain one tree.)"
  (try
    (-> []
        (append-items (parse-journal (find-journal oai-record)))
        (append-items (parse-conf (find-conf oai-record))))
    (catch Exception e (st/print-stack-trace e))))

