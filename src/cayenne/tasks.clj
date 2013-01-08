(ns cayenne.tasks
  (import [java.io PrintWriter])
  (require [clojure.data.json :as json]
           [clojure.java.io :as io]))

(defn doi-record-writer [out-file]
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [doi-record] 
      (.println wrtr (pr-str doi-record)))))

(defn doi-writer [out-file]
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [doi-record]
      (.println wrtr (:doi doi-record)))))

(defn doi-record-json-writer [out-file]
  (let [wrtr (PrintWriter. (io/writer out-file))]
    (fn [doi-record]
      (.println wrtr (json/write-str doi-record)))))

