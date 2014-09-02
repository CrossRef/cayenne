(ns cayenne.api.v1.facet
  (:require [clojure.string :as string]))

(def std-facets
  {"type" {:external-field "type-name"}
   "year" {:external-field "published"}
   "publication" {:external-field "container-title"}
   "category" {:external-field "category-name"}
   "funder_name" {:external-field "funder-name"}
   "funder_doi" {:external-field "funder-doi"
                 :allow-unlimited-values true}
   "orcid" {:external-field "orcid"
            :allow-unlimited-values true}
   "issn" {:external-field "issn"
           :allow-unlimited-values true}
   "source" {:external-field "source"}
   "publisher" {:external-field "publisher-name"}
   "license_url" {:external-field "license"}})

(def external->internal-name
  (into {}
        (map (fn [[key val]] [(get val :external-field) key]) std-facets)))

(defn facet-value-limit [field specified-limit]
  (cond (and (= specified-limit "*")
             (get-in std-facets [field :allow-unlimited-values]))
        -1
        (= specified-limit "*")
        100
        :else
        specified-limit))

(defn apply-facets [solr-query facets]
  (doseq [{:keys [field count]} facets]
    (let [internal-field-name (external->internal-name field)
          limited-count (facet-value-limit internal-field-name count)]
      (if (some #{(string/lower-case field)} ["*" "t" "true" "1"])
        (do
          (.setFacetLimit solr-query (int limited-count))
          (.addFacetField solr-query (into-array String (keys std-facets))))
        (do
          (.addFacetField solr-query (into-array String [internal-field-name]))
          (.setParam solr-query 
                     (str "f." internal-field-name ".facet.limit") 
                     (into-array String [(str limited-count)]))))))
  (doto solr-query
    (.setFacet true)
    (.setFacetMinCount (int 1))))

(defn ->response-facet [solr-facet]
  (let [external-name (get-in std-facets [(.getName solr-facet) :external-field])
        vals (->> (.getValues solr-facet)
                   (map #(vector (.getName %) (.getCount %)))
                   (filter (fn [[_ val]] (not (zero? val))))
                   (sort-by second)
                   reverse
                   flatten
                   (apply array-map))]
    [external-name
     {:value-count (count vals)
      :values vals}]))

(defn ->response-facets [solr-response]
  (into {} (map ->response-facet (.getFacetFields solr-response))))
