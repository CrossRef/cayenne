(ns cayenne.formats.unixref
  (:require [cayenne.xml :as xml])
  (:use [cayenne.ids.doi]))

;; -----------------------------------------------------------------
;; Helpers

(defn parse-attach [item relation loc 
                         & {:keys [single multi]
                            :or {single nil
                                 multi nil}}]
  (prn relation loc)
  (let [existing (or (get item relation) [])]
    (if single
      (assoc item relation (conj existing (single loc)))
      (assoc item relation (concat existing (multi loc))))))

(defn append-items [a b]
  "Appends a map, or concats a list of maps, to an existing item list."
  (if (map? b)
    (conj a b)
    (concat a b)))

;; -----------------------------------------------------------------
;; Component locators

(defn find-journal [record-loc]
  (xml/xselect1 record-loc :> "journal" "journal_metadata"))

(defn find-journal-issue [journal-loc]
  (xml/xselect1 journal-loc "journal_issue"))

(defn find-journal-article [journal-loc]
  (xml/xselect1 journal-loc "journal_article"))

(defn find-journal-volume [issue-loc]
  (xml/xselect1 issue-loc "journal_volume"))

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

(defn find-item-pub-dates [work-loc]
  (xml/xselect work-loc "publication_date"))

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
  (let [media-type (keyword (or (xml/xselect1 pub-date-loc ["media_type"]) :print))
        day-val (xml/xselect1 pub-date-loc "day" :text)
        month-val (xml/xselect1 pub-date-loc "month" :text)
        year-val (xml/xselect1 pub-date-loc "year" :text)]
    {:type :published
     :subtype media-type
     :day day-val 
     :month (parse-month month-val) 
     :year year-val 
     :time-of-year (parse-time-of-year month-val)}))

(defn parse-start-date [start-date-loc]
  (let [month-val (xml/xselect1 start-date-loc "start_month" :text)]
    {:type :start
     :day (xml/xselect1 start-date-loc "start_day" :text)
     :month (parse-month month-val)
     :year (xml/xselect1 start-date-loc "start_year" :text)
     :time-of-year (parse-time-of-year month-val)}))

(defn parse-end-date [end-date-loc]
  (let [month-val (xml/xselect1 end-date-loc "end_month" :text)]
    {:type :end
     :day (xml/xselect1 end-date-loc "end_day" :text)
     :month (parse-month month-val)
     :year (xml/xselect1 end-date-loc "end_year" :text)
     :time-of-year (parse-time-of-year month-val)}))

;; --------------------------------------------------------------
;; Citations

(defn find-item-citations [work-loc]
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

;; ------------------------------------------------------------------
;; Resources

(defn parse-item-resources [item-loc]
  ())

;; --------------------------------------------------------------
;; Identifiers

;; todo timestamp and doi collection info (including crawler resources)
(defn parse-item-doi [item-loc]
  (if-let [doi (xml/xselect1 item-loc "doi_data" "doi" :text)]
    {:type :doi
     :subtype :crossref
     :value (normalize-long-doi doi)
     :original doi}))

(defn find-item-issns [item-loc]
  (xml/xselect item-loc "issn"))

;; todo normalize issn
(defn parse-item-issn [issn-loc]
  (let [issn-type (or (xml/xselect1 issn-loc ["media_type"]) "print")
        issn-value (xml/xselect1 issn-loc :text)]
    {:type :issn
     :subtype (if (= issn-type "print") :p :e)
     :value issn-value
     :original issn-value}))

(defn find-item-isbns [item-loc]
  (xml/xselect item-loc "isbn"))

;; todo normalize isbn
(defn parse-item-isbn [isbn-loc]
  (let [isbn-type (or (xml/xselect1 isbn-loc ["media_type"]) "print")
        isbn-value (xml/xselect1 isbn-loc :text)]
    {:type :isbn
     :subtype (if (= isbn-type "print") :p :e)
     :value isbn-value
     :original isbn-value}))

(defn parse-item-ids [item-loc]
  "Extracts all IDs for an item (DOI, ISSN, ISBN, so on). Extracts into
   maps with the keys :type, :subtype, :value and :original."
  (-> []
      (conj (parse-item-doi item-loc))
      (concat (map parse-item-issn (find-item-issns item-loc)))
      (concat (map parse-item-isbn (find-item-isbns item-loc)))))

;; ---------------------------------------------------------------
;; Generic item parsing

(defn parse-item-citations [item-loc]
  (map parse-citation (find-item-citations item-loc)))

(defn parse-item-pub-dates [item-loc]
  (map parse-pub-date (find-item-pub-dates item-loc)))

; also todo: publisher_item crossmark component_list
(defn parse-item [item-loc]
  "Pulls out metadata that is somewhat standard across types: contributors,
   resource urls, some dates, titles, ids, citations and components."
  (-> {}
      ;(parse-attach :contributor item-loc parse-contributors)
      (parse-attach :id item-loc :multi parse-item-ids)
      ;(parse-attach :resource item-loc :multi parse-resources)
      ;(parse-attach :title item-loc :multi parse-titles)
      (parse-attach :citation item-loc :multi parse-item-citations)
      (parse-attach :date item-loc :multi parse-item-pub-dates)))

;; -----------------------------------------------------------------
;; Specific item parsing

(defn parse-journal-article [article-loc]
  (conj (parse-item article-loc)
        {:type :journal-article
         :first-page (xml/xselect1 article-loc "pages" "first_page" :text)
         :last-page (xml/xselect1 article-loc "pages" "last_page" :text)
         :other-pages (xml/xselect1 article-loc "pages" "other_pages" :text)}))

(defn parse-journal-issue [issue-loc]
  (-> (parse-item issue-loc)
      (parse-attach :component issue-loc :single (comp find-journal-volume parse-journal-volume))
      (parse-attach :component issue-loc :single (comp find-journal-article parse-journal-article))
      (conj {:type :journal-issue
             :numbering (xml/xselect1 issue-loc "special_numbering" :text)
             :issue (xml/xselect1 issue-loc "issue" :text)})))

(defn parse-journal-volume [volume-loc]
  (-> (parse-item volume-loc)
      (conj {:type :journal-volume
             :volume (xml/xselect1 volume-loc "volume" :text)})))

;; todo move full_title and abbrev_title into parse-titles
(defn parse-journal [journal-loc]
  (-> (parse-item journal-loc)
      (parse-attach :component journal-loc :single (comp find-journal-issue parse-journal-issue))
      (parse-attach :component journal-loc :single (comp find-journal-article parse-journal-article))
      (conj
       {:type :journal
        :title (xml/xselect1 journal-loc "full_title" :text)
        :short-title (xml/xselect1 journal-loc "abbrev_title" :text)})))

(defn parse-conf-paper [conf-paper-loc]
  (-> (parse-item conf-paper-loc)
      (conj
       {:type :proceedings-article
        :start-page (xml/xselect1 conf-paper-loc "pages" "start_page")
        :end-page (xml/xselect1 conf-paper-loc "pages" "end_page")
        :other-pages (xml/xselect1 conf-paper-loc "pages" "other_pages")})))

;; todo perhaps make sponsor organisation and event location address first class items.
(defn parse-event [event-loc]
  (-> (parse-item event-loc)
      (parse-attach :date event-loc :single (comp find-event-date parse-start-date))
      (parse-attach :date event-loc :single (comp find-event-date parse-end-date))
      (conj
       {:type :conference
        :name (xml/xselect1 "conference_name" :text)
        :theme (xml/xselect1 "conference_theme" :text)
        :location (xml/xselect1 "conference_location" :text)
        :sponsor (xml/xselect1 "conference_sponsor" :text)
        :acronym (xml/xselect1 "conference_acronym" :text)
        :number (xml/xselect1 "conference_number" :text)})))

(defn parse-conf [conf-loc]
  (let [proceedings-loc (xml/xselect1 conf-loc "proceedings_metadata")]
    (-> (parse-item proceedings-loc)
        (parse-attach :component conf-loc :single (comp find-event parse-event))
        (parse-attach :component conf-loc :single (comp find-conf-paper parse-conf-paper))
        (conj
         {:type :proceedings
          :coden (xml/xselect1 proceedings-loc "coden" :text)
          :subject (xml/xselect1 proceedings-loc "proceedings_subject")
          :volume (xml/xselect1 proceedings-loc "volume")}))))

;(defn parse-book [book-loc]
;  (let [series-loc (xml/xselect1 book-loc "book_metadata" "series_metadata")
;        content-loc (xml/xselect1 book-loc "content_item")]
    

;; ---------------------------------------------------------------

(defn unixref-simple-record-parser [oai-record]
  (let [journal-loc (xml/xselect1 oai-record :> "journal")
        article-loc (xml/xselect1 journal-loc "journal_article")]
    {:doi (xml/xselect1 article-loc "doi_data" "doi" :text)
     :citations (map parse-citation (find-item-citations article-loc))
     :pub-date (first (parse-item-pub-dates article-loc))
     :issn (first (xml/xselect journal-loc "journal_metadata" "issn" :text))}))
        
(defn unixref-record-parser [oai-record]
  "Returns a tree of the items present in an OAI record. Each item has 
   :contributor, :citation and :component keys that may list further items.

   Items can be any of:

   journal
   journal-issue
   journal-volume
   journal-article
   proceedings
   proceedings-article
   conference
   -institution
   -location
   report-article
   standard
   dataset
   citation
   contributor

   Each item may have a list of :id structures, a list of :title structures,
   a list of :date structures, any number of flat :key value pairs, and finally, 
   relationship structures in :contributor, :citation and :component.

   The result of this function is a list of trees (and in most cases the list will
   contain one tree.)"
  (-> []
      (append-items (parse-journal (find-journal oai-record)))
      (append-items (parse-conf (find-conf oai-record)))))

