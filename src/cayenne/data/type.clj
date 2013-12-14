(ns cayenne.data.type
  (:require [cayenne.api.v1.response :as r]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.filter :as filter]
            [cayenne.formats.citeproc :as citeproc]
            [cayenne.ids.type :as type-id]
            [cayenne.conf :as conf]))

(defn ->pretty-type [id t]
  {:id id
   :label (:label t)})

(def solr-type-id-field "type")

(defn get-solr-works [query-context]
  (let [ctxt (update-in query-context [:id] type-id/->index-id)]
    (-> (conf/get-service :solr)
        (.query (query/->solr-query ctxt
                                    :id-field solr-type-id-field
                                    :filters filter/std-filters))
        (.getResults))))

(defn fetch-all []
  (-> (r/api-response :type-list)
      (r/with-result-items 
        (count type-id/type-dictionary)
        (map (fn [[id t]] (->pretty-type id t)) type-id/type-dictionary))))

(defn fetch-one [query-context]
  (let [query-id (:id query-context)
        type-info (->> query-id
                       keyword
                       (get type-id/type-dictionary))]
    (when type-info
      (->> type-info
           (->pretty-type query-id)
           (r/api-response :type :content)))))

(defn fetch-works [query-context]
  (let [doc-list (get-solr-works query-context)]
    (-> (r/api-response :work-list)
        (r/with-query-context-info query-context)
        (r/with-result-items 
          (.getNumFound doc-list)
          (map citeproc/->citeproc doc-list)))))
