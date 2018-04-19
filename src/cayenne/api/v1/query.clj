(ns cayenne.api.v1.query
  (:require [cayenne.conf :as conf]
            [cayenne.util :as util :refer [?> ?>>]]
            [cayenne.api.v1.filter :as filter]
            [cayenne.api.v1.parameters :as p]
            [cayenne.api.v1.facet :as facet]
            [cayenne.api.v1.fields :as fields]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [info error]]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]))

(def default-offset 0)
(def default-rows 20)
(def max-rows 1000)
(def max-offset 10000)
(def max-sample 100)
(def default-facet-rows 20)
(def max-facet-rows 1000)

;; todo this should be passed in to ->query-context
(def sort-fields
  {"score"                  [:_score]
   "relevance"              [:_score]
   "updated"                [:deposited]
   "deposited"              [:deposited]
   "indexed"                [:indexed]
   "created"                [:first-deposited]
   "published-print"        [:published-print]
   "published-online"       [:published-online]
   "published"              [:published]
   "issued"                 [:published]
   "is-referenced-by-count" [:is-referenced-by-count]
   "references-count"       [:references-count]

   ;; for deposits (todo separate these out)
   "submitted"              :submitted-at})

;; todo this should be passed in to ->query-context
(def select-fields
  {"DOI"                    [:doi]
   "member"                 [:member-id]
   "prefix"                 [:owner-prefix]
   "URL"                    [:doi]
   "issued"                 [:published]
   "created"                [:first-deposited]
   "deposited"              [:deposited]
   "indexed"                [:indexed]
   "publisher"              [:publisher]
   "references-count"       [:references-count]
   "is-referenced-by-count" [:is-referenced-by-count]
   "type"                   [:type]
   "content-domain"         [:domain :domain-exclusive]
   "relation"               [:reference.* :relation.*]
   "published-online"       [:published-online]
   "published-print"        [:published-print]
   "posted"                 [:posted]
   "accepted"               [:accepted]
   "content-created"        [:content-created]
   "approved"               [:approved]
   "publisher-location"     [:publisher-location]
   "abstract"               [:abstract-xml]
   "article-number"         [:article-number]
   "volume"                 [:volume]
   "issue"                  [:issue]
   "ISBN"                   [:isbn.*]
   "ISSN"                   [:issn.*]
   "degree"                 [:degree]
   "alternative-id"         [:supplementary-id]
   "title"                  [:title]
   "short-title"            [:short-title]
   "original-title"         [:original-title]
   "subtitle"               [:subtitle]
   "container-title"        [:container-title]
   "short-container-title"  [:short-container-title]
   "group-title"            [:group-title]
   "archive"                [:archive]
   "update-policy"          [:update-policy]
   "update-to"              [:update-to.*]
   "updated-by"             [:updated-by.*]
   "license"                [:license.*]
   "link"                   [:link.*]
   "page"                   [:first-page :last-page]
   "funder"                 [:funder.*]
   "assertion"              [:assertion.*]
   "clinical-trial-number"  [:clinical-trial.*]
   "issn-type"              [:issn.*]
   "event"                  [:event.*]
   "reference"              [:refernece.*]
   "author"                 [:contributor.*]
   "editor"                 [:contributor.*]
   "chair"                  [:contributor.*]
   "translator"             [:contributor.*]
   "standards-body"         [:standards-body.*]
   "score"                  [:_score]})

(defn clean-terms [terms & {:keys [remove-syntax] :or {remove-syntax false}}]
  (if-not remove-syntax
    terms
    (-> terms
        (string/replace #"[\\+!{}*\"\'\.\[\]\(\)\-:;\/%^&?=_,]+" " ")
        (string/replace #"\|\|" " ")
        (string/replace #"&&" " ")
        (string/replace #"\s(?i)not\s" " ")
        (string/replace #"\s(?i)or\s" " ")
        (string/replace #"\s(?i)and\s" " "))))

(defn parse-rows-val [val]
  (int (cond
         (nil? val)
         default-rows
         (= (type val) java.lang.String)
         (util/parse-int-safe val))))

(defn parse-offset-val [val]
  (int (cond
         (nil? val)
         default-offset
         (= (type val) java.lang.String)
         (util/parse-int-safe val))))

(defn parse-sample-val
  "Returns a sample count or 0, indicating that no sample
   is to be taken."
  [val]
  (int (cond
         (nil? val)
         0
         (= (type val) java.lang.String)
         (util/parse-int-safe val))))

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

(defn parse-sort [params]
  (when-let [sort-params (get params :sort)]
    (-> sort-params
        string/trim
        string/lower-case
        sort-fields)))

(defn get-selectors [params]
  (when (get params :select)
    (string/split (get params :select) #",")))

(defn get-field-queries [params]
  (->> params
       (filter #(.startsWith (-> % first name) "query."))
       (map #(vector (-> % first name (string/replace #"query." ""))
                     (-> % second (clean-terms :remove-syntax true))))))

(defn ->query-context [resource-context & {:keys [id filters]
                                           :or {id nil filters {}}}]
  (let [params (p/get-parameters resource-context)]
    {:id id
     :sample (parse-sample-val (:sample params))
     :terms (:query params)
     :field-terms (get-field-queries params)
     :cursor (:cursor params)
     :offset (parse-offset-val (:offset params))
     :rows (parse-rows-val (:rows params))
     :select (get-selectors params)
     :facets (get-facets params)
     :order (parse-sort-order params)
     :sort (parse-sort params)
     :rels (get-rels params)
     :filters (merge filters (get-filters params))
     :debug (:debug params)}))

(defn with-source-fields [es-body query-context]
  (if (empty? (:select query-context))
    es-body
    (assoc es-body :_source (->> (:select query-context)
                                 (map select-fields)
                                 (apply concat)))))

(defn with-filters [es-body query-context & {:keys [filters]}]
  (let [filter-clauses (map #((-> % first name filters)
                              (-> % second first))
                            (:filters query-context))
        filter-occurrence (->> filter-clauses
                               (filter #(= (:occurrence %) :filter))
                               (map :clause))
        must-not-occurrence (->> filter-clauses
                                 (filter #(= (:occurrence %) :must-not))
                                 (map :clause))]
    (-> es-body
        (assoc-in [:query :bool :filter] filter-occurrence)
        (assoc-in [:query :bool :must_not] must-not-occurrence))))

(defn with-query [es-body query-context & {:keys [id-field filters]}]
  (cond-> es-body
    (-> query-context :terms string/blank? not)
    (assoc-in [:query :bool :should]
              [{:term {:metadata-content-text (:terms query-context)}}])

    (-> query-context :prefix-terms string/blank? not)
    (assoc-in [:query :bool :should]
              [{:match_phrase_prefix
                {(:prefix-field query-context) (:prefix-terms query-context)}}])

    ;; todo could be rewritten to use /type/type/id
    id-field
    (update-in [:query :bool :should] conj {:term {id-field (:id query-context)}})

    ;; todo only considering first filter value
    (-> query-context :filters empty? not)
    (with-filters query-context :filters filters)))

(defn with-paging [es-body query-context & {:keys [paged count-only]}]
  (cond
    paged
    (-> es-body
        (assoc :from (or (:offset query-context) 0))
        (assoc :size (:rows query-context)))

    count-only
    (assoc :size 0)

    :else
    es-body))

(defn with-sort-fields [es-body query-context]
  (if (-> query-context :sort empty?)
    es-body
    (assoc es-body :sort (map #(hash-map % {:order (:order query-context)})
                              (:sort query-context)))))

(defn with-random-sort [es-body query-context]
  (if (and (:sample query-context) (not= 0 (:sample query-context)))
    (let [current-query (:query es-body)]
      (-> es-body
          (dissoc :query)
          (assoc-in [:query :function_score :query] current-query)
          (assoc-in [:query :function_score :functions]
                    [{:random_score {:seed (tc/to-long (t/now))
                                     :field :_seq_no}}])
          (assoc :from 0)
          (assoc :size (:sample query-context))))
    es-body))

(defn with-scroll [es-body query-context]
  (cond
    (-> query-context :cursor string/blank?)
    es-body

    (-> query-context :cursor (= "*"))
    (assoc es-body :sort [:_doc])

    (and (-> query-context :cursor string/blank? not)
         (-> query-context :cursor (not= "*")))
    {:scroll "1m"
     :scroll_id (:cursor query-context)}))

(defn ->es-request [query-context
                    & {:keys [index paged id-field filters count-only]
                       :or {index "work" paged true id-field nil filters {}
                            count-only false}}]
  {:method :get
   :url (cond
          (-> query-context :cursor string/blank?)
          (str "/" index "/" index "/_search")
          (and (-> query-context :cursor string/blank? not)
               (-> query-context :cursor (not= "*")))
          "/_search/scroll"
          (-> query-context :cursor (= "*"))
          (str "/" index "/" index "/_search?scroll=1m"))
   :body
   (-> {}
       (with-source-fields query-context)
       (with-sort-fields query-context)
       (with-query query-context :id-field id-field :filters filters)
       (with-paging query-context :paged paged :count-only count-only)
       (facet/with-aggregations query-context)
       (fields/with-field-queries query-context)
       (with-random-sort query-context)
       (with-scroll query-context))})

(defn prefix-query-context [query-context prefix-field]
  (-> query-context
      (assoc :prefix-terms (:terms query-context))
      (assoc :prefix-field prefix-field)
      (dissoc :terms)))
