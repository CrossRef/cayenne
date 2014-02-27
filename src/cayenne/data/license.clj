(ns cayenne.data.license
  (:require [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.filter :as filter]
            [cayenne.conf :as conf]))

(defn ->license-doc [facet-field-value]
  {:URL (.getName facet-field-value)
   :work-count (.getCount facet-field-value)})

;; todo why are odd license URLs appearing with 0 counts?

(defn fetch-all [query-context]
  (let [facet-field (-> (conf/get-service :solr)
                        (.query (query/->solr-query
                                 (assoc query-context :facets ["license_url"])
                                 :filters filter/std-filters))
                        (.getFacetField "license_url"))
        facet-values (->> (.getValues facet-field)
                          (filter #(not= 0 (.getCount %))))]
    (-> (r/api-response :license-list)
        (r/with-result-items
          (map ->license-doc facet-values)
          (count facet-values))
        (r/with-query-context-info query-context))))

  
