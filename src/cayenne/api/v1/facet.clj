(ns cayenne.api.v1.facet
  (:require [clojure.string :as string]))

(def std-facets
  {"type" {:external-field "type-name"}
   "year" {:external-field "published"}
   "publication" {:external-field "container-title"}
   "category" {:external-field "category-name"}
   "funder_name" {:external-field "funder-name"}
   "source" {:external-field "source"}
   "publisher" {:external-field "publisher-name"}
   "license_url" {:external-field "license"}})

(def external->internal-name
  (into {}
        (map (fn [[key val]] [(get val :external-field) key]) std-facets)))

(defn apply-facets [solr-query facets]
  (doseq [{:keys [field count]} facets]
    (let [internal-field-name (external->internal-name field)]
      (if (some #{(string/lower-case field)} ["*" "t" "true" "1"])
        (do
          (.setFacetLimit solr-query (int count))
          (.addFacetField solr-query (into-array String (keys std-facets))))
        (do
          (.addFacetField solr-query (into-array String [internal-field-name]))
          (.setParam solr-query 
                     (str "f." internal-field-name ".facet.limit") 
                     (into-array String [(str count)]))))))
  (doto solr-query
    (.setFacet true)
    (.setFacetMinCount (int 1))))

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
