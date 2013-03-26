(ns cayenne.tasks.dump
  (:import [java.io PrintWriter])
  (:require [clojure.data.json :as json]
           [clojure.java.io :as io]))

(defn record-writer [out-file]
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [records] 
      (doseq [record records]
        (.println wrtr (pr-str record))))))

(defn record-json-writer [out-file]
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [records]
      (doseq [record records]
        (.println wrtr (json/write-str record))))))

(defn record-tab-writer [out-file]
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [records]
      (doseq [record records]
        (.println wrtr (str (:full-name record) \tab (:short-name record)))))))

;; these are not generic and should move elsewhere:

(defn doi-writer [out-file]
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [doi-record]
      (.println wrtr (:doi doi-record)))))

(defn text-citation-writer [out-file]
  (let [wrtr (PrintWriter. (io/writer out-file))
        write-citation (fn [citation] 
                         (when (:unstructured citation)
                           (.println wrtr (:unstructured citation))))]
    (fn [doi-record]
      (doseq [citation (:citations doi-record)]
        (write-citation citation)))))

(defn journal-title-writer [out-file]
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [doi-record]
      (let [journal (:journal doi-record)]
        (when (and (:title journal) (:short-title journal))
          (.println wrtr (str (:title journal) \tab (:short-title journal))))))))

