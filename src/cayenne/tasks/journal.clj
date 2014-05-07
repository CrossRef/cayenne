(ns cayenne.tasks.journal
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [somnium.congomongo :as m]
            [cayenne.conf :as conf]
            [cayenne.ids.issn :as issn-id]
            [cayenne.util :as util]))

;; ingest journal information
;; currently only the CrossRef title list csv

(defn ensure-journal-indexes! [collection]
  (m/add-index! collection [:issn])
  (m/add-index! collection [:title])
  (m/add-index! collection [:token]))

(defn insert-journal! [collection name publisher issns]
  (let [normalized-issns (map issn-id/normalize-issn issns)
        doc {:title name
             :token (util/tokenize-name name)
             :publisher publisher
             :issn normalized-issns}]
    (m/update! collection
               {:issn normalized-issns}
               doc)))

(defn load-journals-from-cr-title-list-csv [collection]
  (m/with-mongo (conf/get-service :mongo)
    (ensure-journal-indexes! collection)
    (with-open [body (io/reader (conf/get-param [:location :cr-titles-csv]))]
      (let [cleaned (string/replace (slurp body) #"\\\"" "")]
        (doseq [title (drop 1 (csv/read-csv cleaned))]
          (insert-journal! collection
                           (first title)
                           (second title)
                           (string/split (nth title 3) #"\|")))))))
