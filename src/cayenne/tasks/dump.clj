(ns cayenne.tasks.dump
  (:import [java.io PrintWriter])
  (:require [clojure.data.json :as json]
           [clojure.java.io :as io]))

(defn record-writer [out-file]
  "Write whole records as clojure serialized data structures."
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [records] 
      (doseq [record records]
        (.println wrtr (pr-str record))))))

(defn record-json-writer [out-file]
  "Write whole records as JSON."
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [records]
      (doseq [record records]
        (.println wrtr (json/write-str record))))))

;; these are not generic and should move elsewhere:

(defn record-tab-writer [out-file]
  "Write whole records as TSV."
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [records]
      (doseq [record records]
        (.println wrtr (str (:full-name record) \tab (:short-name record)))))))

(defn doi-writer [out-file]
  "Write only DOIs to a file."
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [doi-record]
      (.println wrtr (:doi doi-record)))))

(defn text-citation-writer [out-file]
  "Write only unstructured citation texts to a file."
  (let [wrtr (PrintWriter. (io/writer out-file))
        write-citation (fn [citation] 
                         (when (:unstructured citation)
                           (.println wrtr (:unstructured citation))))]
    (fn [doi-record]
      (doseq [citation (:citations doi-record)]
        (write-citation citation)))))

; doi year citation issn science_cat url url_top_level 
; needs science category, host top level type
(defn citation-info-writer [out-file]
  "Write only citing DOI, publication date, unstructured citation text
   and ISSN to a file, one line per citation."
  (let [wrtr (PrintWriter. (io/writer out-file))
        write-citation (fn [record citation] 
                         (when (:unstructured citation)
                           (.println wrtr 
                                     (str (:doi record) \tab
                                          (get-in record [:pub-date :year]) \tab
                                          (:issn record) \tab
                                          (:unstructured citation)))))]
    (fn [doi-record]
      (when (and (:doi doi-record) (:issn doi-record) (:pub-date doi-record))
        (doseq [citation (:citations doi-record)]
          (write-citation doi-record citation))))))

(defn journal-title-writer [out-file]
  "Write journal title and short titles to a file."
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [doi-record]
      (let [journal (:journal doi-record)]
        (when (and (:title journal) (:short-title journal))
          (.println wrtr (str (:title journal) \tab (:short-title journal))))))))

