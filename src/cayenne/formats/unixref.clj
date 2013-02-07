(ns cayenne.formats.unixref
  (:require [cayenne.xml :as xml]))

(defn find-journal-article [record-loc]
  (xml/xselect1 record-loc :> "journal" "journal_article"))

(defn find-conf-proc [record-loc]
  (xml/xselect1 record-loc :> "conference" "conference_paper"))

(defn find-work-doi [work-loc]
  (xml/xselect1 work-loc "doi_data" "doi" :text))

; todo nil should not come back from xselect - empty list should
(defn find-work-citations [work-loc]
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

(defn find-pub-date [work-loc]
  (xml/xselect1 work-loc :> "publication_date"))

(defn parse-citation [citation-loc]
  {:doi (xml/xselect1 citation-loc "doi" :text)
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

(defn unixref-record-parser [oai-record]
  (if-let [article (find-journal-article oai-record)]
    {:type :journal-article
     :citations (map parse-citation (find-work-citations article))
     :doi (find-work-doi article)}))
