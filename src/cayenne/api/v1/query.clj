(ns cayenne.api.v1.query
  (:require [cayenne.conf :as conf]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [info error]])
  (:import [org.apache.solr.client.solrj SolrQuery]))

;; todo complete validation of query context params
;; and error response on invalid params

(def default-offset 0)
(def default-rows 20)
(def max-rows 1000)

(defn parse-rows-val [val]
  (int (cond
        (nil? val)
        default-rows
        (= (type val) java.lang.String)
        (min (Integer/parseInt val) max-rows)
        :else
        (min val max-rows))))

(defn parse-offset-val [val]
  (int (cond
        (nil? val)
        default-offset
        (= (type val) java.lang.String)
        (Integer/parseInt val)
        :else
        val)))
    
(defn get-filters [params]
  (when (get params :filter)
    (into {}
          (let [filter-list (string/split (get params :filter) #",")]
            (map #(string/split % #":") filter-list)))))

(defn ->query-context [resource-context & {:keys [id] :or {id nil}}]
  (if-not (nil? (get-in resource-context [:request :body]))
    (let [json-body (-> resource-context 
                        (get-in [:request :body]) 
                        (io/reader) 
                        (json/read :key-fn keyword))]
      {:id id
       :terms (:query json-body)
       :offset (-> json-body (:offset) (parse-offset-val))
       :rows (-> json-body (:rows) (parse-rows-val))
       :filters (:filter json-body)})
    {:id id
     :terms (get-in resource-context [:request :params :query])
     :offset (-> resource-context (get-in [:request :params :offset]) (parse-offset-val))
     :rows (-> resource-context (get-in [:request :params :rows]) (parse-rows-val))
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
      (doto query
        (.setStart (:offset query-context))
        (.setRows (:rows query-context))))
    (when count-only
      (doto query
        (.setRows (int 0))))
    query))
