(ns cayenne.api.v1.filter
  (:require [clj-time.core :as dt]
            [clojure.string :as string]
            [cayenne.util :as util]
            [cayenne.conf :as conf]
            [cayenne.ids.fundref :as fundref]
            [cayenne.ids.type :as type-id]
            [cayenne.ids.prefix :as prefix]
            [cayenne.ids.issn :as issn]
            [cayenne.ids.orcid :as orcid]
            [cayenne.ids.doi :as doi-id]
            [somnium.congomongo :as m]
            [clojure.string :as string]))

;; Solr filter helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn field-is [field-name match]
  (str field-name ":" match))

(defn field-is-esc [field-name match]
  (str field-name ":\"" match "\""))

(defn field-gt [field-name val]
  (str field-name ":[" (+ 1 val) " TO *]"))

(defn field-gte [field-name val]
  (str field-name ":[" val " TO *]"))

(defn field-lt [field-name val]
  (str field-name ":[* TO " (- val 1) "]"))

(defn field-lte [field-name val]
  (str field-name ":[* TO " val "]"))

(defn q-or [& more]
  (str "(" (string/join " " (interpose "OR" more)) ")"))

(defn q-and [& more]
  (str "(" (string/join " " (interpose "AND" more)) ")"))

(defn field-lt-or-gt [field-name val end-point]
  (cond 
   (= end-point :from)
   (field-gt field-name val)
   (= end-point :until)
   (field-lt field-name val)))

(defn field-lte-or-gte [field-name val end-point]
  (cond
   (= end-point :from)
   (field-gte field-name val)
   (= end-point :until)
   (field-lte field-name val)))

(defn split-date [date-str]
  (let [date-parts (string/split date-str #"-")]
    {:year (Integer/parseInt (first date-parts))
     :month (Integer/parseInt (nth date-parts 1 "-1"))
     :day (Integer/parseInt (nth date-parts 2 "-1"))}))

(defn obj-date [date-str]
  (let [date-parts (split-date date-str)]
    (cond (not= (:day date-parts) -1)
          (dt/date-time (:year date-parts) (:month date-parts) (:day date-parts))
          (not= (:month date-parts) -1)
          (dt/date-time (:year date-parts) (:month date-parts))
          :else
          (dt/date-time (:year date-parts)))))

;; Solr filters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        
(defn stamp-date [date-stamp-field direction]
  (fn [val]
    (let [d (obj-date val)]
      (if (= direction :from)
        (str date-stamp-field ":[" d " TO *]")
        (str date-stamp-field ":[* TO " d "]")))))

(defn particle-date [year-field month-field day-field end-point]
  (fn [val]
    (let [d (split-date val)]
      (cond (not= (:day d) -1)
            (q-or
             (field-lt-or-gt year-field (:year d) end-point)
             (q-and (field-is year-field (:year d))
                    (field-lt-or-gt month-field (:month d) end-point))
             (q-and (field-is year-field (:year d))
                    (field-is month-field (:month d))
                    (field-lte-or-gte day-field (:day d) end-point)))
            (not= (:month d) -1)
            (q-or
             (field-lt-or-gt year-field (:year d) end-point)
             (q-and (field-is year-field (:year d))
                    (field-lte-or-gte month-field (:month d) end-point)))
            (:year d)
            (field-lte-or-gte year-field (:year d) end-point)))))

(defn greater-than-zero [field]
  (fn [val]
    (str field ":[1 TO *]")))

(defn existence [field]
  (fn [val]
    (cond (#{"t" "true" "1"} (.toLowerCase val))
          (str field ":[* TO *]")
          (#{"f" "false" "0"} (.toLowerCase val))
          (str "-" field ":[* TO *]"))))

(defn bool [field]
  (fn [val]
    (cond (#{"t" "true" "1"} (.toLowerCase val))
          (str field ":true")
          (#{"f" "false" "0"} (.toLowerCase val))
          (str field ":false"))))

(defn equality [field & {:keys [transformer] :or {transformer identity}}]
  (fn [val] (str field ":\"" (transformer val) "\"")))

(defn compound [prefix ordering & {:keys [transformers matchers]
                                   :or {transformers {} matchers {}}}]
  (fn [m]
    (let [field-names (filter m ordering)
          field-name-parts (butlast field-names)
          value-name-part (last field-names)]
      (str prefix
           "_"
           (apply str (interpose "_" field-names))
           (when (not (empty? field-name-parts)) "_")
           (apply str (->> field-name-parts
                           (map #(if (transformers %)
                                   ((transformers %) (get m %))
                                   (get m %)))
                           (interpose "_")))
           (if (matchers value-name-part)
             ((matchers value-name-part) (get m value-name-part))
             (str ":\"" (get m value-name-part) "\""))))))

(defn generated
  "Generate a list of filter values from a single query-provided value."
  [field & {:keys [generator]}]
  (fn [val]
    (->> (generator val)
         (map #(field-is-esc field %))
         (apply q-or))))
         
(defn member-prefix-generator [value]
  (let [val (Integer/parseInt value)
        member-doc (m/with-mongo (conf/get-service :mongo)
                     (m/fetch-one "members" :where {:id val}))]
    (if member-doc
      (map prefix/to-prefix-uri (:prefixes member-doc))
      ["nothing"]))) ; 'nothing' forces filter to match nothing if we have no prefixes

;; Mongo filters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mongo-stamp-date [field direction]
  (fn [val]
    (let [date-val (obj-date val)]
      (cond (= direction :from)
            {field {"$gte" date-val}}
            (= direction :until)
            {field {"$lte" date-val}}))))

(defn mongo-equality [field & {:keys [transformer] :or {transformer identity}}]
  (fn [val]
    {field (transformer val)}))

(defn mongo-bool [field]
  (fn [val]
    (cond (#{"t" "true" "1"} (.toLowerCase val))
          {field true}
          (#{"f" "false" "0"} (.toLowerCase val))
          {field false})))

;; Filter definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def std-filters
  {"from-update-date" (stamp-date "deposited_at" :from)
   "until-update-date" (stamp-date "deposited_at" :until)
   "from-index-date" (stamp-date "indexed_at" :from)
   "until-index-date" (stamp-date "indexed_at" :until)
   "from-deposit-date" (stamp-date "deposited_at" :from)
   "until-deposit-date" (stamp-date "deposited_at" :until)
   "from-pub-date" (particle-date "year" "month" "day" :from)
   "until-pub-date" (particle-date "year" "month" "day" :until)
   "from-issued-date" (particle-date "year" "month" "day" :from)
   "until-issued-date" (particle-date "year" "month" "day" :until)
   "is-update" (existence "update_doi")
   "updates" (equality "update_doi" :transformer doi-id/to-long-doi-uri)
   "has-full-text" (existence "full_text_url")
   "has-license" (existence "license_url")
   "has-references" (greater-than-zero "citation_count")
   "has-update-policy" (existence "update_policy")
   "has-archive" (existence "archive")
   "has-orcid" (existence "orcid")
   "has-funder" (existence "funder_name")
   "full-text" (compound "full_text" ["type" "version"]
                         :transformers {"type" util/slugify})
   "license" (compound "license" ["url" "version" "delay"]
                       :transformers {"url" util/slugify}
                       :matchers {"delay" #(str ":[* TO " % "]")})
   "directory" (equality "oa_status" :transformer string/upper-case)
      ;; watch the above - oa_status field changing to directory soon
   "archive" (equality "archive")
   "issn" (equality "issn" :transformer issn/to-issn-uri)
   "type" (equality "type" :transformer type-id/->index-id)
   "orcid" (equality "orcid" :transformer orcid/to-orcid-uri)
   "doi" (equality "doi_key" :transformer doi-id/to-long-doi-uri)
   "member" (generated "owner_prefix" :generator member-prefix-generator)
   "prefix" (equality "owner_prefix" :transformer prefix/to-prefix-uri)
   "funder" (equality "funder_doi" :transformer fundref/id-to-doi-uri)})

(def deposit-filters
  {"from-submission-time" (mongo-stamp-date "submitted-at" :from)
   "until-submission-time" (mongo-stamp-date "submitted-at" :until)
   "status" (mongo-equality "status")
   "owner" (mongo-equality "owner")
   "type" (mongo-equality "content-type")
   "doi" (mongo-equality "dois" :transformer doi-id/normalize-long-doi)
   "test" (mongo-bool "test")})
