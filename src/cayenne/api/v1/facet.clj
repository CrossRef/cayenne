(ns cayenne.api.v1.facet
  (:require [clojure.string :as string]))

(def std-facets
  {"type"                    {:external-field "type-name"
                              :allow-unlimited-values true}
   "issued-year"             {:external-field "published"
                              :allow-unlimited-values true}
   "container-title"         {:external-field "container-title"}
   "funder-name"             {:external-field "funder-name"
                              :allow-unlimited-values true}
   "funder-doi"              {:external-field "funder-doi"
                              :allow-unlimited-values true}
   "contributor-orcid"       {:external-field "orcid"
                              :allow-unlimited-values true}
   "issn.value"              {:external-field "issn"
                              :allow-unlimited-values true}
   "publisher"               {:external-field "publisher-name"
                              :allow-unlimited-values true}
   "license-url"             {:external-field "license"
                              :allow-unlimited-values true}
   "archive"                 {:external-field "archive"
                              :allow-unlimited-values true}
   "update-type"             {:external-field "update-type"
                              :allow-unlimited-values true}
   "relation-type"           {:external-field "relation-type"
                              :allow-unlimited-values true}
   "contributor-affiliation" {:external-field "affiliation"
                              :allow-unlimited-values true}
   "assertion-name"          {:external-field "assertion"
                              :allow-unlimited-values true}
   "assertion-group-name"    {:external-field "assertion-group"
                              :allow-unlimited-values true}
   "link-application"        {:external-field "link-application"}
   "volume"                  {:external-field "journal-volume"
                              :allow-unlimited-values true}
   "issue"                   {:external-field "journal-issue"
                              :allow-unlimited-values true}})

(def external->internal-name
  (into {}
        (map (fn [[key val]] [(get val :external-field) key]) std-facets)))

(defn facet-value-limit [field specified-limit]
  (cond (and (= specified-limit "*")
             (get-in std-facets [field :allow-unlimited-values]))
        100000
        (= specified-limit "*")
        1000
        :else
        specified-limit))

;; todo should handle multiple nested aggs at the same path
(defn with-aggregations [es-body {:keys [facets]}]
  (reduce
   (fn [es-body {:keys [field count]}]
     (let [internal-field-name (external->internal-name field)
           limited-count (facet-value-limit internal-field-name count)
           nested-path (get-in std-facets [internal-field-name :nested-path])]
       (assoc-in
        es-body
        [:aggs internal-field-name]
        {:terms {:field internal-field-name
                 :size limited-count}})))
   es-body
   facets))

(defn ->response-facet [aggregation]
  (let [internal-field-name (first aggregation)
        buckets (-> aggregation second :buckets)]
    [(get-in std-facets [(name internal-field-name) :external-field])
     {:value-count (count buckets)
      :values (into {}
                    (map
                     #(vector (:key %) (:doc_count %))
                     buckets))}]))

(defn ->response-facets [aggregations]
  (into {} (map ->response-facet aggregations)))
