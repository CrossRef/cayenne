(ns cayenne.api.v1.facet
  (:require [clojure.string :as string]))

(def std-facets
  {"type" {:external-field "type"}
   "year" {:external-field "published"}
   "publication" {:external-field "container-title"}
   "category" {:external-field "category-name"}
   "funder_name" {:external-field "funder-name"}
   "source" {:external-field "source"}
   "publisher" {:external-field "publisher-name"}})

(defn apply-facets [solr-query facets]
  (doseq [facet-field facets]
    (prn facet-field)
    (if (some #{(string/lower-case facet-field)} ["t" "true" "1"])
      (.addFacetField solr-query (into-array String (keys std-facets)))
      (.addFacetField solr-query (into-array String [facet-field]))))
  (doto solr-query
    (.setFacet true)
    (.setFacetLimit (int 10))))

(defn remove-zero-values [facet-map]
  (->> facet-map
       (filter (fn [[_ val]] (not (zero? val))))
       (into {})))

(defn ->response-facet [solr-facet]
  (let [external-name (get-in std-facets [(.getName solr-facet) :external-field])
        vals (->> (.getValues solr-facet)
                   (map #(vector (.getName %) (.getCount %)))
                   (into {})
                   remove-zero-values)]
    [external-name
     {:value-count (count vals)
      :values vals}]))

(defn ->response-facets [solr-response]
  (into {} (map ->response-facet (.getFacetFields solr-response))))
