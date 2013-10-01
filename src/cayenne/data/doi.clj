(ns cayenne.data.doi
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [cayenne.formats.citeproc :as citeproc]
            [somnium.congomongo :as m]
            [clojure.string :as string]))

;; todo eventually produce citeproc from more detailed data stored in mongo
;; for each DOI that comes back from solr. For now, covert the solr fields
;; to some (occasionally ambiguous) citeproc structures.

;; todo API links - orcids, subject ids, doi, issn, isbn, owner prefix

;; todo conneg. currently returning two different formats - item-tree
;; where a DOI is known, citeproc for search results.

(defn fetch [query-context]
  (let [doc-list (-> (conf/get-service :solr)
                     (.query (query/->solr-query query-context))
                     (.getResults))]
    (-> (r/api-response :work-list)
        (r/with-result-items (.getNumFound doc-list) (map citeproc/->citeproc doc-list))
        (r/with-query-context-info query-context))))

(defn fetch-one
  "Fetch a known DOI, which we take from mongo."
  [doi-uri]
  (let [work (m/with-mongo (conf/get-service :mongo)
               (m/fetch-one "items" :where {:id doi-uri}))]
    (-> (r/api-response :work
                        :content work))))

(defn fetch-random [count]
  (m/with-mongo (conf/get-service :mongo)
    (let [c (or (try (Integer/parseInt count) (catch Exception e nil)) 50)
          records (m/fetch "dois"
                           :where {:random_index {"$gte" (rand)}}
                           :limit c
                           :sort {:random_index 1})]
      (r/api-response :id-list :content (map :doi records)))))

