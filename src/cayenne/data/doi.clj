(ns cayenne.data.doi
  (:require [cayenne.conf :as conf]
            [cayenne.api.query :as query]
            [cayenne.api.response :as r]))

(defn fetch-dois [query-context]
  (let [doc-list (-> (conf/get-service :solr)
                     (.query (query/->solr-query query-context))
                     (.getResults))]
    (-> (r/api-response :doi-result-list)
        (r/with-result-items (.getNumFound doc-list) doc-list)
        (r/with-query-context-info query-context))))

        


