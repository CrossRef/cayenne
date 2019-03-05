(ns cayenne.tasks.journal
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [cayenne.conf :as conf]
            [cayenne.ids.issn :as issn-id]
            [cayenne.util :as util]
            [cayenne.ids.doi :as doi-id]
            [qbits.spandex :as elastic]
            [cayenne.ids.issn :as issn]
            [cayenne.elastic.util :as elastic-util]))

(defn issns [csv-row]
  (let [p-issn (:pissn csv-row)
        e-issn (:eissn csv-row)]
    (cond-> []
      (not (string/blank? p-issn))
      (conj {:value (issn/normalize-issn p-issn) :type "print"})
      (not (string/blank? e-issn))
      (conj {:value (issn/normalize-issn e-issn) :type "electronic"}))))

(defn index-command
  "Convert a parsed CSV row and turn into an Elastic Search bulk index command."
  [csv-row]
  (let [title (:JournalTitle csv-row)
        journal-id (:JournalID csv-row)]
    [{:index {:_id (Long/parseLong journal-id)}}
     {:title     title
      :token     (util/tokenize-name title)
      :id        (Long/parseLong journal-id)
      :doi       (-> csv-row :doi doi-id/normalize-long-doi)
      :publisher (:Publisher csv-row)
      :issn      (issns csv-row)}]))

(defn fetch-titles
  "Given an open reader, return a sequence of title entries as hashmaps.
   Remember to consume the lazy seq before closing the reader!"
  [rdr]
  (let [rows (csv/read-csv rdr)
        ; Header rows as keywords.
        header (->> rows first (map keyword))
        entries (rest rows)]
    (map #(apply merge (map hash-map header %)) entries)))

(def chunk-size
  "Items in an index command. Note that this must be divisible by 2,
   as it's concatenated pairs of index-command, body."
  100)

(defn index-journals []
  (with-open [rdr (io/reader (conf/get-param [:location :cr-titles-csv]))]
    (let [titles (fetch-titles rdr)
          index-items (mapcat index-command titles)]
      (doseq [chunk (partition-all chunk-size index-items)]
        (elastic/request
          (conf/get-service :elastic)
            {:method :post
             :url "journal/journal/_bulk"
             :body (elastic-util/raw-jsons chunk)})))))
