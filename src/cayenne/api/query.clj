(ns cayenne.api.query
  (:require [cayenne.conf :as conf])
  (:import [org.apache.solr.client.solrj SolrQuery]))

;; todo validation of query context params

(defn get-filters [params]
  (into {}
        (filter (fn [k v] (.startsWith (name k) "f."))
                params)))

(defn ->query-context [resource-context]
  (conf/log (get-in resource-context [:request :params :q]))
  {:terms (get-in resource-context [:request :params :q])
   :page (or (get-in resource-context [:request :params :p]) 1)
   :rows (or (get-in resource-context [:request :params :r]) 20)
   :filters []});(get-filters (get-in resource-context [:request :params]))})

(defn clean-terms [terms] terms)

(defn ->solr-query [query-context & filter-converter]
  (let [filter-converter (or (first filter-converter) identity)
        query (doto (SolrQuery.)
                (.setQuery (-> query-context (:terms) (clean-terms)))
                (.addField "*")
                (.addField "score")
                (.setHighlight true)
                (.setFacet true)
                (.setStart (int (:page query-context)))
                (.setRows (int (:rows query-context))))]
    query))
    


