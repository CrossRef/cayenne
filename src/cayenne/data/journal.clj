(ns cayenne.data.journal
  (:require [cayenne.conf :as conf]
            [cayenne.util :as util]
            [cayenne.data.work :as work]
            [cayenne.ids.issn :as issn-id]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [somnium.congomongo :as m]))

(defn ->issn-types [journal-doc]
  (cond-> []
    (:eissn journal-doc)
    (conj {:value (:eissn journal-doc) :type "electronic"})

    (:pissn journal-doc)
    (conj {:value (:pissn journal-doc) :type "print"})))

(defn ->response-doc [journal-doc subject-docs]
  {:title (:title journal-doc)
   :publisher (:publisher journal-doc)
   :ISSN (:issn journal-doc)
   :issn-type (->issn-types journal-doc)
   :subjects (map #(hash-map :ASJC (Integer/parseInt (:code %))
                             :name (:name %))
                  subject-docs)
   :flags (:flags journal-doc)
   :coverage (:coverage journal-doc)
   :breakdowns (:breakdowns journal-doc)
   :counts (:counts journal-doc)
   :last-status-check-time (:last-status-check-time journal-doc)})

(defn issn-doc->subjects [issn-doc]
  (m/with-mongo (conf/get-service :mongo)
    (m/fetch "categories" :where {:code {:$in (or (:categories issn-doc) [])}})))

(defn get-subject-docs [issns]
  (let [query-issns (or issns [])
        issn-docs (m/with-mongo (conf/get-service :mongo)
                    (m/fetch "issns"
                             :where {:$or [{:p_issn {:$in query-issns}}
                                           {:e_issn {:$in query-issns}}]}))]
    (mapcat issn-doc->subjects issn-docs)))

(defn fetch-one [query-context]
  (when-let [journal-doc (m/with-mongo (conf/get-service :mongo)
                           (m/fetch-one "journals"
                                        :where {:issn (:id query-context)}))]
    (r/api-response
     :journal
     :content (->response-doc journal-doc
                              (get-subject-docs (:issn journal-doc))))))

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
        (r/with-result-items
          result-count
          (map #(->response-doc % (get-subject-docs (:issn %))) docs)))))

(defn fetch-works [query-context]
  (-> query-context
      (assoc-in [:filters :issn] (issn-id/to-issn-uri (:id query-context)))
      work/fetch))
