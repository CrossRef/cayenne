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
             (filter (fn [[k v]] (.startsWith (name k) "fl.")))
             (map (fn [[k v]] [(apply str (drop 2 (name k))) v])))))

(defn ->query-context [resource-context & {:keys [id] :or {id nil}}]
  (if (:body resource-context)
    (let [json-body (-> resource-context (:body) (json/read-str))]
      {:id id
       :terms (:q json-body)
       :page (or (:p json-body) "1")
       :rows (or (:r json-body) "20")
       :filters (:fl json-body)})
    {:id id
     :terms (get-in resource-context [:request :params :q])
     :page (or (get-in resource-context [:request :params :p]) "1")
     :rows (or (get-in resource-context [:request :params :r]) "20")
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
      (let [rows (Integer/parseInt (:rows query-context))
            start-index (* (Integer/parseInt (:page query-context)) rows)]
        (doto query
          (.setStart start-index)
          (.setRows rows))))
    (when count-only
      (doto query
        (.setRows (int 0))))
    query))

