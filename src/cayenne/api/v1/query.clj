(ns cayenne.api.v1.query
  (:require [cayenne.conf :as conf]
            [cayenne.util :as util :refer [?> ?>>]]
            [cayenne.api.v1.filter :as filter]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [info error]])
  (:import [org.apache.solr.client.solrj SolrQuery SolrQuery$ORDER]))

;; todo complete validation of query context params
;; and error response on invalid params

(def default-offset 0)
(def default-rows 20)
(def max-rows 1000)

(defn random-field []
  (str "random_" (rand-int Integer/MAX_VALUE)))

(defn parse-rows-val [val]
  (int (cond
        (nil? val)
        default-rows
        (= (type val) java.lang.String)
        (min (max 0 (Integer/parseInt val)) max-rows)
        :else
        (min (max 0 val) max-rows))))

(defn parse-offset-val [val]
  (int (cond
        (nil? val)
        default-offset
        (= (type val) java.lang.String)
        (max 0 (Integer/parseInt val))
        :else
        (max 0 val))))

(defn parse-sample-val
  "Returns a sample count or nil, indicating that no sample
   is to be taken."
  [val]
  (int (cond
        (nil? val)
        0
        (= (type val) java.lang.String)
        (max 0 (Integer/parseInt val))
        :else
        (max 0 val))))
    
(defn get-filters 
  "Turns a filter value string such as a.b:val,c:val2 into
   a map representation, such as {\"a\" {\"b\" val} \"c\" val2}."
  [params]
  (when (get params :filter)
    (->> (string/split (get params :filter) #",")
         (map #(let [parts (string/split % #":")
                     k (first parts)
                     val (string/join ":" (rest parts))
                     path (string/split k #"\.")]
                 [path val]))
         (reduce (fn [m [path val]] (assoc-in m path val)) {}))))

(defn get-selectors [params]
  (when (get params :selector)
    (string/split (get params :selector) #",")))

(defn ->query-context [resource-context & {:keys [id] :or {id nil}}]
  (if-not (nil? (get-in resource-context [:request :body]))
    (let [json-body (-> resource-context 
                        (get-in [:request :body])
                        (io/reader)
                        (json/read :key-fn keyword))]
      {:id id
       :sample (-> json-body (:sample) (parse-sample-val))
       :terms (:query json-body)
       :offset (-> json-body (:offset) (parse-offset-val))
       :rows (-> json-body (:rows) (parse-rows-val))
       :selectors (:selector json-body)
       :filters (:filter json-body)})
    {:id id
     :sample (-> resource-context (get-in [:request :params :sample]) (parse-sample-val))
     :terms (get-in resource-context [:request :params :query])
     :offset (-> resource-context (get-in [:request :params :offset]) (parse-offset-val))
     :rows (-> resource-context (get-in [:request :params :rows]) (parse-rows-val))
     :selectors (get-selectors (get-in resource-context [:request :params]))
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
      (let [ids (if (vector? (:id query-context))
                  (:id query-context)
                  [(:id query-context)])
            fl-str (->> ids
                        (map #(str id-field ":\"" % "\""))
                        (apply filter/q-or))]
        (.addFilterQuery query
                         (into-array String [fl-str]))))
    (doseq [[filter-name filter-val] (:filters query-context)]
      (when (filters filter-name)
        (doto query
          (.addFilterQuery (into-array String [((filters filter-name) filter-val)])))))
    (when paged
      (doto query
        (.setStart (:offset query-context))
        (.setRows (:rows query-context))))
    (when (not= 0 (:sample query-context))
      (doto query
        (.setStart 0)
        (.setRows (:sample query-context))
        (.setSort (random-field) SolrQuery$ORDER/asc)))
    (when count-only
      (doto query
        (.setRows (int 0))))
    query))

(defn ->mongo-query [query-context
                     & {:keys [where sort] :or [where {} sort {}]}]
  [:where where 
   :sort sort
   :limit (:rows query-context)
   :skip (:offset query-context)])
