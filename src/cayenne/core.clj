(ns cayenne.core
  (:import [java.util.concurrent Executors])
  (:require [cayenne.xml :as xml])
  (:use [clojure.java.io :only [file reader]]))

; todo error reporting on pool-processing threads

(def debug-processing false)
(def pool-processing true)

(def process-citations-full false)
(def process-citation-ids true)
(def process-works-full false)

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
   :unstructured (xml/xselect1 citation-loc "unstructured" :text)})

(defn parse-citation-ids [citation-loc]
  {:doi (xml/xselect1 citation-loc "doi" :text)})

(defn parse-oai-record [oai-record]
  (if-let [article (find-journal-article oai-record)]
    {:type :journal-article
     :citations (map parse-citation (find-work-citations article))
     :doi (find-work-doi article)}))

(def processing-pool 
  (Executors/newFixedThreadPool (+ 2 (.. Runtime getRuntime availableProcessors))))

(defn process-file [task-fn file]
  (with-open [rdr (reader file)]
    (xml/process-xml rdr "record" (comp task-fn parse-oai-record))))

(defn process-file-in-pool [pool task-fn file]
  (when debug-processing
    (prn (str "Executing " file)))
  (.execute pool #(process-file task-fn file)))

(defn xml-file? [file]
  (and (.isFile file) (.endsWith (.getName file) ".xml")))

(defn xml-file-seq [dir count]
  (if (= count :all)
    (->> (file-seq dir)
         (filter xml-file?))
    (->> (file-seq dir)
         (filter xml-file?)
         (take count))))

(defn process-dir [dir & {:keys [count task] :or {count :all task identity}}]
  (doseq [file (xml-file-seq dir count)]
    (if pool-processing
      (process-file-in-pool processing-pool task file)
      (process-file task file))))

(defmacro with-pool-timing [pool & body]
  "Time a pool to completion of all its submitted tasks. All tasks should be
   submitted by body. After body is evaluated, the pool is instructed to shutdown
   after its tasks have finished."
  `(let [start-time# (System/currentTimeMillis)]
     (do ~@body)
     (.shutdown ~pool)
     (.awaitTermination ~pool 1 (java.util.concurrent.TimeUnit/DAYS))
     (let [end-time# (System/currentTimeMillis)
           elapsed-time# (- end-time# start-time#)]
       (prn "Total elapsed millis:" elapsed-time#))))


