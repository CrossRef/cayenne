(ns cayenne.api.v1.query
  (:require [cayenne.conf :as conf]
            [cayenne.util :as util :refer [?> ?>>]]
            [cayenne.api.v1.filter :as filter]
            [cayenne.api.v1.parameters :as p]
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
  "Returns a sample count or 0, indicating that no sample
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

(defn get-facets
  [params]
  (prn (get params :facet))
  (if (get params :facet)
    (-> (get params :facet)
        (string/split #",")
        (string/trim))
    []))

(defn get-selectors [params]
  (when (get params :selector)
    (string/split (get params :selector) #",")))      

(defn ->query-context [resource-context & {:keys [id filters] 
                                           :or {id nil filters {}}}]
  (let [params (p/get-parameters resource-context)]
    {:id id
     :sample (parse-sample-val (:sample params))
     :terms (:query params)
     :offset (parse-offset-val (:offset params))
     :rows (parse-rows-val (:rows params))
     :selectors (get-selectors params)
     :facets (get-facets params)
     :filters (merge filters (get-filters params))}))

;; todo get selectors and get filters handle json input

(defn clean-terms [terms & {:keys [remove-syntax] :or {remove-syntax false}}] 
  (if (not remove-syntax)
    terms
    (-> terms
        (string/replace #"[+\-!(){}\[\]\^\"~*?:\\]+" " ")
        (string/replace #"||" " ")
        (string/replace #"&&" " ")
        (string/replace #"(?i)or" " ")
        (string/replace #"(?i)and" " "))))

(defn make-query-string [query-context]
  (str
   (if (:terms query-context)
     (-> query-context :terms clean-terms)
     "*:*")
   (when (:raw-terms query-context)
     (str " " (-> query-context :raw-terms)))))

(defn ->solr-query [query-context &
                    {:keys [paged id-field filters count-only]
                     :or {paged true 
                          id-field nil 
                          filters {}
                          count-only false}}]
  (let [query (doto (SolrQuery.)
                (.setQuery (make-query-string query-context))
                (.addField "*")
                (.addField "score")
                (.setHighlight true)
                (.setFacet true))]
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
      (let [filter-name-s (name filter-name)]
        (when (filters filter-name-s)
          (doto query
            (.addFilterQuery (into-array String [((filters filter-name-s) filter-val)]))))))
    (when (:raw-filter query-context)
      (doto query
        (.addFilterQuery (into-array String [(:raw-filter query-context)]))))
    (when paged
      (doto query
        (.setStart (:offset query-context))
        (.setRows (:rows query-context))))
    (when (and (:sample query-context) (not= 0 (:sample query-context)))
      (doto query
        (.setStart (int 0))
        (.setRows (:sample query-context))
        (.setSort (random-field) SolrQuery$ORDER/asc)))
    (when-not (empty? (:facets query-context))
      (doto query
        (.setFacet true)
        (.setFacetLimit (int -1))
        (.addFacetField (into-array String (:facets query-context)))))
    (when count-only
      (doto query
        (.setRows (int 0))))
    query))

(defn ->mongo-query [query-context
                     & {:keys [where sort filters id-field] 
                        :or {where {} sort {} filters {} id-field nil}}]
  (let [filter-where (into {} 
                           (map (fn [[n v]]
                                  ((filters (name n)) v))
                                (:filters query-context)))]
    (concat
     [:where (merge
              where
              filter-where
              (when id-field {id-field (:id query-context)}))]
     (when sort
       [:sort sort])
     (when (:rows query-context)
       [:limit (:rows query-context)])
     (when (:offset query-context)
       [:skip (:offset query-context)]))))
                 
