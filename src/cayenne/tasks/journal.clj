(ns cayenne.tasks.journal
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [somnium.congomongo :as m]
            [cayenne.conf :as conf]
            [cayenne.ids.issn :as issn-id]
            [cayenne.util :as util]
            [cayenne.ids.doi :as doi-id]))

;; ingest journal information
;; currently only the CrossRef title list csv

(defn ensure-journal-indexes! [collection]
  (m/add-index! collection [:id])
  (m/add-index! collection [:issn])
  (m/add-index! collection [:title])
  (m/add-index! collection [:token]))

(defn insert-journal! [collection name id publisher doi pissn eissn]
  (let [normalized-pissn (issn-id/normalize-issn pissn)
        normalized-eissn (issn-id/normalize-issn eissn)
        normalized-issns (->> [normalized-eissn, normalized-pissn]
                              (remove nil?))
        normalized-doi (doi-id/normalize-long-doi doi)
        journal-id (Integer/parseInt id)
        doc {:title name
             :id journal-id
             :doi normalized-doi
             :token (util/tokenize-name name)
             :publisher publisher
             :pissn normalized-pissn
             :eissn normalized-eissn
             :issn normalized-issns}]
    (m/update! collection {:id journal-id} doc)))

(def title-column 0)
(def id-column 1)
(def publisher-column 2)
(def subjects-column 3)
(def pissn-column 4)
(def eissn-column 5)
(def doi-column 6)
(def issues-column 7)

(defn load-journals-from-cr-title-list-csv [collection]
  (m/with-mongo (conf/get-service :mongo)
    (ensure-journal-indexes! collection)
    (with-open [body (io/reader (conf/get-param [:location :cr-titles-csv]))]
      (let [cleaned (string/replace (slurp body) #"\\\"" "")]
        (doseq [title (drop 1 (csv/read-csv cleaned))]
          (insert-journal! collection
                           (nth title title-column)
                           (nth title id-column)
                           (nth title publisher-column)
                           (nth title doi-column)
                           (nth title pissn-column)
                           (nth title eissn-column)))))))

