(ns cayenne.formats.unixref
  (:require [cayenne.xml :as xml])
  (:use [cayenne.ids.doi]))

(defn find-journal [record-loc]
  (xml/xselect1 record-loc :> "journal" "journal_metadata"))

(defn find-journal-issue [record-loc]
  (xml/xselect1 record-loc :> "journal" "journal_issue"))

(defn find-journal-article [record-loc]
  (xml/xselect1 record-loc :> "journal" "journal_article"))

(defn find-journal-volume [record-loc]
  (xml/xselect1 record-loc :> "journal" "journal_issue" "journal_volume"))

(defn find-book [record-loc]
  ())

(defn find-book-series [record-loc]
  ())

(defn find-conference [record-loc]
  ())

(defn find-proceedings [record-loc]
  (xml/xselect1 record-loc :> "conference" "proceedings_metadata"))

(defn find-event [record-loc]
  "Sometimes present in a proceedings. Never has a DOI."
  ())

(defn find-conference-paper [record-loc]
  "Found in conferences."
  (xml/xselect1 record-loc :> "conference" "conference_paper"))

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

(defn find-item-pub-dates [work-loc]
  (xml/xselect work-loc "publication_date"))

; todo nil should not come back from xselect - empty list should
(defn find-item-citations [work-loc]
  (let [r (xml/xselect work-loc "citation_list" "citation")]
    (if (nil? r)
      '()
      r)))

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
  (let [type (keyword (or (xml/xselect1 pub-date-loc ["media_type"]) :print))
        day-val (xml/xselect1 pub-date-loc "day" :text)
        month-val (xml/xselect1 pub-date-loc "month" :text)
        year-val (xml/xselect1 pub-date-loc "year" :text)]
    {:type type
     :day day-val 
     :month (parse-month month-val) 
     :year year-val 
     :time-of-year (parse-time-of-year month-val)}))

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

(defn parse-item-resources [item-loc]
  ())

;; todo timestamp and doi collection info (including crawler resources)
(defn parse-item-doi [item-loc]
  (if-let [doi (xml/xselect1 item-loc "doi_data" "doi" :text)]
    {:type :doi
     :subtype :crossref
     :value (normalize-long-doi doi)
     :original doi}))

(defn find-item-issns [item-loc]
  (xml/xselect item-loc "issn"))

(defn parse-item-issn [issn-loc]
  (let [issn-type (or (xml/xselect1 issn-loc ["media_type"]) "print")
        issn-subtype (if (= issn-type "print") :p :e)
        issn-value (xml/xselect1 issn-loc :text)]
    {:type :issn
     :subtype issn-subtype
     ;:value (normalize-issn issn-value)
     :original issn-value}))

(defn parse-item-ids [item-loc]
  "Extracts all IDs for an item (DOI, ISSN, ISBN, so on). Extracts into
   maps with the keys :type, :subtype, :value and :original."
  (-> []
      (conj (parse-item-doi item-loc))
      (concat (map parse-item-issn (find-item-issns item-loc)))))

(defn parse-item-citations [item-loc]
  (map parse-citation (find-item-citations item-loc)))

(defn parse-item-pub-dates [item-loc]
  (map parse-pub-date (find-item-pub-dates item-loc)))

(defn parse-item [item-loc]
  "Pulls out metadata that is somewhat standard across types: contributors,
   resource urls, some dates, titles, ids, citations and components."
  {:citation (parse-item-citations item-loc)
   ;;:contributor
   ;;:title
   ;;:resources
   :date [{:published (parse-item-pub-dates item-loc)}]
   :id (parse-item-ids item-loc)})

(defn parse-journal-article [article-loc]
  (conj (parse-item article-loc)
        {:type :journal-article
         :first-page (xml/xselect1 article-loc "pages" "first_page" :text)
         :last-page (xml/xselect1 article-loc "pages" "last_page" :text)
         :other-pages (xml/xselect1 article-loc "pages" "other_pages" :text)}))

;; todo can be more than one title and abbrev title
(defn parse-journal [journal-loc]
  (conj (parse-item journal-loc)
        {:type :journal
         :title (xml/xselect1 journal-loc "full_title" :text)
         :short-title (xml/xselect1 journal-loc "abbrev_title" :text)}))

(defn parse-journal-issue [issue-loc]
  (conj (parse-item issue-loc)
        {:type :journal-issue
         :numbering (xml/xselect1 issue-loc "special_numbering" :text)
         :issue (xml/xselect1 issue-loc "issue" :text)}))

(defn parse-journal-volume [volume-loc]
  (conj (parse-item volume-loc)
        {:type :journal-volume}))

(defn append-items [a b]
  "Appends a map, or concats a list of maps, to an existing item list."
  (if (map? b)
    (conj a b)
    (concat a b)))

(defn unixref-record-parser [oai-record]
  "Returns a tree of the items present in an OAI record. Each item has 
   :contributor, :citation and :component keys that may list further items.

   Items can be any of:
   journal
   journal-issue
   journal-volume
   journal-article
   proceedings-article
   conference
   institution
   location
   report-article
   standard
   dataset
   citation
   contributor

   Each item may have a list of :id structures, a list of :title structures,
   a list of :date structures, any number of flat :key value pairs, and finally, 
   relationship structures in :contributor, :citation and :component."
  (-> []
      (append-items (parse-journal-article (find-journal-article oai-record)))
      (append-items (parse-journal-issue (find-journal-issue oai-record)))
      (append-items (parse-journal-volume (find-journal-volume oai-record)))
      (append-items (parse-journal (find-journal oai-record)))))

