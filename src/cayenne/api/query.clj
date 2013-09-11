(ns cayenne.api.query
  (:require [cayenne.conf :as conf])
  (:import [org.apache.solr.client.solrj SolrQuery]))

;; todo validation of query context params

(defn get-filters [params]
  (into {}
        (filter (fn [k v] (.startsWith (name k) "f."))
                params)))

(defn ->query-context [resource-context & {:keys [id] :or {:id nil}}]
  {:id id
   :terms (get-in resource-context [:request :params :q])
   :page (or (get-in resource-context [:request :params :p]) 1)
   :rows (or (get-in resource-context [:request :params :r]) 20)
   :filters []});(get-filters (get-in resource-context [:request :params]))})

(defn clean-terms [terms] terms)

(defn ->solr-query [query-context &
                    {:keys [paged id-field filter-converter count-only]
                     :or {:paged true :id-field nil :filter-converter identity
                          :count-only false}}]
  (let [query (doto (SolrQuery.)
                (.setQuery (-> query-context (:terms) (clean-terms)))
                (.addField "*")
                (.addField "score")
                (.setHighlight true)
                (.setFacet true))]
    (when-not (:terms query-context)
      (doto query
        (.setQuery "*:*")))
    (when id-field
      (doto query
        (.addFilterQuery (into-array String [(str id-field ":\"" (:id query-context) "\"")]))))
    (when paged
      (doto query
        (.setStart (int (:page query-context)))
        (.setRows (int (:rows query-context)))))
    (when count-only
      (doto query
        (.setRows (int 0))))
    query))

(defn ->mongo-query [query-context]
  {})
