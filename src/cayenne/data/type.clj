(ns cayenne.data.type
  (:require [cayenne.api.v1.response :as r]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.filter :as filter]
            [cayenne.api.v1.facet :as facet]
            [cayenne.data.work :as work]
            [cayenne.ids.type :as type-id]
            [cayenne.conf :as conf]))

(defn ->pretty-type [id t]
  {:id id
   :label (:label t)})

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
  (work/fetch query-context :id-field :type))
