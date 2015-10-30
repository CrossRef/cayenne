(ns cayenne.api.v1.query
  (:require [cayenne.conf :as conf]
            [cayenne.util :as util :refer [?> ?>>]]
            [cayenne.api.v1.filter :as filter]
            [cayenne.api.v1.parameters :as p]
            [cayenne.api.v1.facet :as facet]
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
(def default-facet-rows 20)
(def max-facet-rows 1000)

(defn random-field []
  (str "random_" (rand-int Integer/MAX_VALUE)))

(defn parse-rows-val [val]
  (int (cond
        (nil? val)
        default-rows
        (= (type val) java.lang.String)
        (min (max 0 (util/parse-int-safe val)) max-rows)
        :else
        (min (max 0 val) max-rows))))

(defn parse-offset-val [val]
  (int (cond
        (nil? val)
        default-offset
        (= (type val) java.lang.String)
        (max 0 (util/parse-int-safe val))
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
        (max 0 (util/parse-int-safe val))
        :else
        (max 0 val))))
    
(defn get-filters 
  "Turns a filter value string such as a.b:val,c:val2 into
   a map representation, such as {\"a\" {\"b\" val} \"c\" val2}."
  [params]
  (when (get params :filter)
    (->> (string/split (get params :filter) #",")
         (reduce #(if (re-find #":" %2)
                    (conj %1 %2)
                    (conj (drop-last %1) (apply str (last %1) "," %2)))
                 [])
         (map #(let [parts (string/split % #":")
                     k (first parts)
                     val (string/join ":" (rest parts))
                     path (string/split k #"\.")]
                 [path val]))
         (reduce (fn [m [path val]] (update-in m path #(conj %1 val))) {}))))

;; facet spec is
;; field1:count1,...fieldn:countn
;; *, t, T, 1 all signify 'all fields'
(defn get-facets
  [params]
  (if-let [facet-params (get params :facet)]
    (map 
     #(let [[field count] (string/split % #":")]
        {:field field
         :count (if (= count "*")
                  "*"
                  (max 1
                     (min (or (util/parse-int-safe count) 
                              default-facet-rows) 
                          max-facet-rows)))})
     (string/split facet-params #","))
    []))

(defn get-rels
  [params]
  (if-let [rel-params (get params :rel)]
    (map
     #(let [[rel value] (string/split % #":" 2)]
        {:rel (keyword rel)
         :value value
         :any (= value "*")})
     (string/split rel-params #","))
    []))

(defn parse-sort-order [params]
  (if (get params :order)
    (let [val (-> (get params :order)
                  string/trim
                  string/lower-case)]
      (cond (some #{val} ["1" "asc"])
            :asc
            (some #{val} ["-1" "desc"])
            :desc
            :else
            :desc))
    :desc))

;; todo this should be passed in to ->query-context
(def sort-fields
  {"score" ["score"]
   "relevance" ["score"]
   "updated" ["deposited_at"]
   "deposited" ["deposited_at"]
   "indexed" ["indexed_at"]
   "published" ["year" "month" "day"]

   ;; for deposits (todo separate these out)
   "submitted" :submitted-at})

(defn parse-sort [params]
  (when-let [sort-params (get params :sort)]
    (-> sort-params
        string/trim
        string/lower-case
        sort-fields)))
    
(defn get-selectors [params]
  (when (get params :selector)
    (string/split (get params :selector) #",")))

(defn ->query-context [resource-context & {:keys [id filters] 
                                           :or {id nil filters {}}}]
  (let [params (p/get-parameters resource-context)]
    {:id id
     :sample (parse-sample-val (:sample params))
     :terms (:query params)
     :cursor (:cursor params)
     :offset (parse-offset-val (:offset params))
     :rows (parse-rows-val (:rows params))
     :selectors (get-selectors params)
     :facets (get-facets params)
     :order (parse-sort-order params)
     :sort (parse-sort params)
     :rels (get-rels params)
     :filters (merge filters (get-filters params))}))

;; todo get selectors and get filters handle json input

(defn clean-terms [terms & {:keys [remove-syntax] :or {remove-syntax false}}] 
  (if-not remove-syntax
    terms
    (-> terms
        (string/replace #"[\\+!{}*\"\.\[\]\(\)\-:;\/%^&?=_,]+" " ")
        (string/replace #"\|\|" " ")
        (string/replace #"&&" " ")
        (string/replace #"(?i)not" " ")
        (string/replace #"(?i)or" " ")
        (string/replace #"(?i)and" " "))))

(defn make-query-string [query-context]
  (cond (and (:terms query-context)
             (:raw-terms query-context))
        (str (-> query-context :terms (clean-terms :remove-syntax true))
             " "
             (-> query-context :raw-terms))
        (:terms query-context)
        (-> query-context :terms (clean-terms :remove-syntax true))
        (:raw-terms query-context)
        (-> query-context :raw-terms)
        :else
        "*:*"))

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
                (.setHighlight false)
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
          (if (not (sequential? filter-val))
            (doto query
              (.addFilterQuery (into-array String [((filters filter-name-s) filter-val)])))
            (let [filter-fn (filters filter-name-s)
                  filter-query-str (->> filter-val 
                                        (map filter-fn)
                                        (string/join " OR "))]
              (doto query
                (.addFilterQuery (into-array String [(str "(" filter-query-str ")")]))))))))
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
    (when (:sort query-context)
      (doseq [sort-field (:sort query-context)]
        (let [sort-order (if (= (:order query-context) :desc)
                           SolrQuery$ORDER/desc
                           SolrQuery$ORDER/asc)]
          (.addSort query sort-field sort-order))))
    (when (:cursor query-context)
      (doto query
        (.setStart (int 0))
        (.addSort "doi_key" SolrQuery$ORDER/asc)
        (.setParam "cursorMark" (into-array [(:cursor query-context)]))))
    (when-not (empty? (:facets query-context))
      (facet/apply-facets query (:facets query-context)))
    (when count-only
      (doto query
        (.setRows (int 0))))
    query))

(defn ->mongo-query [query-context
                     & {:keys [where filters id-field] 
                        :or {where {} filters {} id-field nil}}]
  (let [filter-where (into {} 
                           (map (fn [[n v]]
                                    ((filters (name n)) v))
                                (:filters query-context)))]
    (concat
     [:where (merge
              where
              filter-where
              (when id-field {id-field (:id query-context)}))]
     (when (:sort query-context)
       [:sort {(:sort query-context)
               (if (= (:order query-context) :asc) 1 -1)}])
     (when (:rows query-context)
       [:limit (:rows query-context)])
     (when (:offset query-context)
       [:skip (:offset query-context)]))))
                 










