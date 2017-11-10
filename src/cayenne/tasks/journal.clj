(ns cayenne.tasks.journal
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [somnium.congomongo :as m]
            [cayenne.conf :as conf]
            [cayenne.ids.issn :as issn-id]
            [cayenne.util :as util]
            [cayenne.ids.doi :as doi-id]
            [qbits.spandex :as elastic]
            [cayenne.ids.issn :as issn]
            [cayenne.elastic.util :as elastic-util]))

(def title-column 0)
(def id-column 1)
(def publisher-column 2)
(def subjects-column 3)
(def pissn-column 4)
(def eissn-column 5)
(def doi-column 6)
(def issues-column 7)

(defn issns [csv-row]
  (let [p-issn (nth csv-row pissn-column)
        e-issn (nth csv-row eissn-column)]
    (cond-> []
      (not (string/blank? p-issn))
      (conj {:value (issn/normalize-issn p-issn) :kind "print"})
      (not (string/blank? e-issn))
      (conj {:value (issn/normalize-issn e-issn) :kind "electronic"}))))

(defn index-command [csv-row]
  (let [title (nth csv-row title-column)
        journal-id (nth csv-row id-column)]
    [{:index {:_id (Long/parseLong journal-id)}}
     {:title     title
      :token     (util/tokenize-name title)
      :id        (Long/parseLong journal-id)
      :doi       (-> csv-row (nth doi-column) doi-id/normalize-long-doi)
      :publisher (nth csv-row publisher-column)
      :issn      (issns csv-row)}]))
           
(defn index-journals []
  (with-open [body (io/reader (conf/get-param [:location :cr-titles-csv]))]
    (let [cleaned (string/replace (slurp body) #"\\\"" "")]
      (doseq [titles (partition-all 100 (drop 1 (csv/read-csv cleaned)))]
        (elastic/request
         (conf/get-service :elastic)
         {:method :post
          :url "journal/journal/_bulk"
          :body (->> titles
                     (map index-command)
                     flatten
                     elastic-util/raw-jsons)})))))
