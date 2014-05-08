(ns cayenne.data.journal
  (:require [cayenne.conf :as conf]
            [cayenne.util :as util]
            [cayenne.data.work :as work]
            [cayenne.ids.issn :as issn-id]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [somnium.congomongo :as m]))

(defn ->response-doc [journal-doc]
  {:name (:title journal-doc)
   :publisher (:publisher journal-doc)
   :issn (:issn journal-doc)})

(defn fetch-one [query-context]
  (when-let [journal-doc (m/with-mongo (conf/get-service :mongo)
                           (m/fetch-one "journals" :where {:issn (:id query-context)}))]
    (r/api-response :journal :content (->response-doc journal-doc))))

(defn ->search-terms [query-context]
  (if (:terms query-context)
    (util/tokenize-name (:terms query-context))
    []))

(defn fetch [query-context]
  (let [search-terms (->search-terms query-context)
        and-list (map #(hash-map "token" {"$regex" (str "^" %)}) search-terms)
        where-clause (if (empty? and-list) {} {"$and" and-list})
        mongo-query (query/->mongo-query query-context :where where-clause)
        docs (if (and (:rows query-context) (zero? (:rows query-context)))
               []
               (m/with-mongo (conf/get-service :mongo)
                 (apply m/fetch "journals" mongo-query)))
        result-count (m/with-mongo (conf/get-service :mongo)
                       (apply m/fetch-count "journals" mongo-query))]
    (-> (r/api-response :journal-list)
        (r/with-query-context-info query-context)
        (r/with-result-items result-count (map ->response-doc docs)))))

(defn fetch-works [query-context]
  (-> query-context
      (assoc :filter {:issn (issn-id/to-issn-uri (:id query-context))})
      work/fetch))
