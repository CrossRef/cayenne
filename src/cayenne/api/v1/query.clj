(ns cayenne.api.v1.query
  (:require [cayenne.conf :as conf]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [taoensso.timbre :as timbre :refer [info error]])
  (:import [org.apache.solr.client.solrj SolrQuery]))

;; todo validation of query context params

(defn get-filters [params]
  (into {}
        (->> params
             (filter (fn [[k v]] (.startsWith (name k) "filter.")))
             (map (fn [[k v]] [(apply str (drop 2 (name k))) v])))))

(defn ->query-context [resource-context & {:keys [id] :or {id nil}}]
  (if (:body resource-context)
    (let [json-body (-> resource-context (:body) (json/read-str))]
      {:id id
       :terms (:query json-body)
       :offset (or (:offset json-body) "1")
       :rows (or (:rows json-body) "20")
       :filters (:filter json-body)})
    {:id id
     :terms (get-in resource-context [:request :params :query])
     :offset (or (get-in resource-context [:request :params :offset]) "0")
     :rows (or (get-in resource-context [:request :params :rows]) "20")
     :filters (get-filters (get-in resource-context [:request :params]))}))

(defn clean-terms [terms] terms)

(defn ->solr-query [query-context &
                    {:keys [paged id-field filters count-only]
                     :or {paged true 
                          id-field nil 
                          filters {}
                          count-only false}}]
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
    (doseq [[filter-name filter-val] (:filters query-context)]
      (when (filters filter-name)
        (doto query
          (.addFilterQuery (into-array String [((filters filter-name) filter-val)])))))
    (when paged
      (let [rows (-> query-context (:rows) (Integer/parseInt))
            offset (-> query-context (:offset) (Integer/parseInt))]
        (doto query
          (.setStart offset)
          (.setRows rows))))
    (when count-only
      (doto query
        (.setRows (int 0))))
    query))

