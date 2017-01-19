(ns cayenne.api.v1.validate
  (:require [clojure.string :as string]
            [clojure.set :as cset]
            [clj-time.format :as tf]
            [cayenne.util :as util]
            [cayenne.api.v1.filter :as f]
            [cayenne.api.v1.parameters :as p]
            [cayenne.api.v1.facet :as fac]
            [cayenne.api.v1.query :as q]
            [cayenne.api.v1.fields :as qf]
            [cayenne.ids.type :as type-id]
            [cayenne.ids.issn :as issn-id]
            [cayenne.ids.doi :as doi-id]
            [cayenne.ids.orcid :as orcid-id]
            [cayenne.ids.prefix :as prefix-id]))

(defn fail [context failed-value error-type message]
  (let [failures-so-far (or (:failures context) [])]
    (assoc context
      :failures
      (conj failures-so-far
            {:type error-type
             :value failed-value
             :message message}))))

(defn pass [context] context)

(defn validate [context pass-fail-fn fail-type fail-message fail-val]
  (if (pass-fail-fn)
    (pass context)
    (fail context fail-val fail-type fail-message)))

(defn string-validator [context s]
  (pass context))

(defn enum-validator [type friendly-name enums]
  (fn [context s]
    (if (some #{s} enums)
      (pass context)
      (fail context s type
            (str friendly-name " specified as " s " but must be one of: "
                 (string/join ", " enums))))))

(def sort-order-validator
  (enum-validator :sort-order-not-valid
                  "Sort order"
                  ["asc" "desc" "1" "-1"]))

(def sort-field-validator
  (enum-validator :sort-field-not-valid
                  "Sort field"
                  (keys q/sort-fields)))

(def version-validator
  (enum-validator :version-not-valid
                  "Version"
                  ["vor" "am" "tdm"]))

(def intended-application-validator
  (enum-validator :intended-application-not-valid
                  "Intended application"
                  ["unspecified" "text-mining"]))

(def boolean-validator
  (enum-validator :boolean-not-valid
                  "Boolean"
                  ["t" "true" "1" "f" "false" "0"]))

(def directory-validator
  (enum-validator :directory-not-valid
                  "Directory"
                  ["DOAJ"]))

(def type-validator
  (enum-validator :type-not-valid
                  "Type"
                  (->> type-id/type-dictionary
                       keys
                       (map name))))

(def archive-validator
  (enum-validator :archive-not-valid
                  "Archive"
                  ["Portico" "CLOCKSS" "DWT"]))

(def deposit-status-validator
  (enum-validator :deposit-status-not-valid
                  "Deposit status"
                  ["completed" "failed" "submitted"]))

(def funder-doi-asserted-by-validator
  (enum-validator :funder-doi-asserted-by-not-valid
                  "Funder DOI asserted by party"
                  ["crossref" "publisher"]))

(defn integer-validator [context s & {:keys [max message] :or {max :infinite message nil}}]
  (if (or (not s)
          (and (re-matches #"\d+" s)
               (if (= max :infinite) true (<= (util/parse-int-safe s) max))))
    (pass context)
    (fail context s :integer-not-valid
          (str "Integer specified as " s " but must be a positive integer"
               (when (not= max :infinite)
                 (str " less than or equal to " max))
               ". "
               message))))

(defn date-validator [context s]
  (if (re-matches #"(\d{4})|(\d{4}-\d{2})|(\d{4}-\d{2}-\d{2})" s)
    (try
      (let [d (tf/parse (:date-parser tf/formatters) (.trim s))]
        (pass context))
      (catch Exception e
        (fail context s :date-not-legitimate
              (str "Date " s " specified in the correct format but represents an"
                   " illegitimate date."))))
    (fail context s :date-not-valid
          (str "Date specified as " s " but must be of the form: "
               "yyyy or yyyy-MM or yyyy-MM-dd"))))

(defn content-type-validator [context s]
  (if (re-matches #"[\w\-]+\/[\w\-+\.]+" s)
    (pass context)
    (fail context s :content-type-not-valid
          (str "Content type specified as " s " but must be "
               "a valid MIME type without wildcards"))))

(defn issn-validator [context s]
  (if (-> s issn-id/normalize-issn issn-id/is-issn?)
    (pass context)
    (fail context s :issn-not-valid
          (str "ISSN specified as " s " but must be of the form: "
               "aaaa-aaab where a is a digit, and b is a digit or 'X'"))))

(defn url-validator [context s] (pass s))

(defn doi-validator [context s]
  (if (-> s doi-id/normalize-long-doi doi-id/is-long-doi?)
    (pass context)
    (fail context s :doi-not-valid
          (str "DOI specified as " s " but must be of the form: "
               "10.prefix/suffix where prefix is 4 or more digits and suffix "
               " is a string"))))
  
(defn orcid-validator [context s]
  (if (-> s orcid-id/normalize-orcid orcid-id/is-orcid?)
    (pass context)
    (fail context s :orcid-not-valid
          (str "ORCID specified as " s " but must be of the form: "
               "aaaa-aaaa-aaaa-aaab where a is a digit and b is a digit or 'X'"))))

(defn prefix-validator [context s]
  (if (-> s prefix-id/normalize-prefix prefix-id/is-prefix?)
    (pass context)
    (fail context s :prefix-not-valid
          (str "Prefix specified as " s " but must be of the form: "
               "10.digits where digits is 4 or more digits"))))

(defn funder-id-validator [context s]
  (if (or (re-matches #"\d+" s)
          (-> s doi-id/normalize-long-doi doi-id/is-long-doi?))
    (pass context)
    (fail context s :funder-id-not-valid
          (str "Funder ID specified as " s " but must be a DOI or "
               "positive integer"))))
  
(def work-filter-validators
  {:from-update-date date-validator
   :until-update-date date-validator
   :from-index-date date-validator
   :until-index-date date-validator
   :from-deposit-date date-validator
   :until-deposit-date date-validator
   :from-pub-date date-validator
   :until-pub-date date-validator
   :from-issued-date date-validator
   :until-issued-date date-validator
   :from-online-pub-date date-validator
   :until-online-pub-date date-validator
   :from-print-pub-date date-validator
   :until-print-pub-date date-validator
   :from-created-date date-validator
   :until-created-date date-validator
   :from-posted-date date-validator
   :until-posted-date date-validator
   :from-accepted-date date-validator
   :until-accepted-date date-validator
   :from-event-start-date date-validator
   :until-event-start-date date-validator
   :from-event-end-date date-validator
   :until-event-end-date date-validator
   :has-event boolean-validator
   :is-update boolean-validator
   :has-update boolean-validator
   :updates doi-validator
   :update-type string-validator
   :has-content-domain boolean-validator
   :has-domain-restriction boolean-validator
   :content-domain string-validator
   :has-abstract boolean-validator
   :has-full-text boolean-validator
   :has-license boolean-validator
   :has-references boolean-validator
   :has-update-policy boolean-validator
   :has-archive boolean-validator
   :has-orcid boolean-validator
   :has-authenticated-orcid boolean-validator
   :has-affiliation boolean-validator
   :has-funder boolean-validator
   :has-funder-doi boolean-validator
   :funder-doi-asserted-by funder-doi-asserted-by-validator
   :has-award boolean-validator
   :has-assertion boolean-validator
   :directory directory-validator
   :archive archive-validator
   :article-number string-validator
   :issn issn-validator
   :type type-validator
   :type-name string-validator
   :orcid orcid-validator
   :assertion string-validator
   :assertion-group string-validator
   :doi doi-validator
   :group-title string-validator
   :container-title string-validator
   :publisher-name string-validator
   :category-name string-validator
   :funder-name string-validator
   :member integer-validator
   :prefix prefix-validator
   :funder funder-id-validator
   :alternative-id string-validator
   :full-text.type content-type-validator
   :full-text.application intended-application-validator
   :full-text.version version-validator
   :license.url url-validator
   :license.version version-validator
   :license.delay integer-validator
   :award.funder funder-id-validator
   :award.number string-validator
   :has-clinical-trial-number boolean-validator
   :clinical-trial-number string-validator})

(def deposit-filter-validators
  {:from-submission-time date-validator
   :until-submission-time date-validator
   :status deposit-status-validator
   :owner string-validator
   :type content-type-validator
   :doi doi-validator
   :test boolean-validator})

(def member-filter-validators
  {:prefix prefix-validator
   :has-public-references boolean-validator
   :backfile-doi-count integer-validator
   :current-doi-count integer-validator})

(def funder-filter-validators
  {:location string-validator})

(defn validate-filters [filter-validators context filters]
  (let [unknown-filters (cset/difference
                         (set (map first filters))
                         (set (map name (keys filter-validators))))
        existence-chk-context 
        (if (empty? unknown-filters)
          (pass context)
          (reduce #(fail %1 %2 :filter-not-available
                         (str "Filter " %2 " specified but there "
                              "is no such filter for this route."
                              " Valid filters for this route are: "
                              (string/join ", " (map name (keys filter-validators)))))
                  context
                  unknown-filters))]
    (reduce
     #(let [validator (-> %2 first keyword filter-validators)]
        (if validator
          (validator %1 (second %2))
          %1))
     existence-chk-context
     filters)))
    
(def validate-deposit-filters (partial validate-filters
                                       deposit-filter-validators))
(def validate-work-filters (partial validate-filters
                                    work-filter-validators))
(def validate-member-filters (partial validate-filters
                                      member-filter-validators))
(def validate-funder-filters (partial validate-filters
                                      funder-filter-validators))

(def wildcard-facet-forms ["*" "t" "T" "1"])

;; TODO check * only for allow unlimited values, number is up to q/max-facet-rows

(defn validate-facets [facet-definitions context facets]
  (let [facet-names (conj (->> facet-definitions vals (map :external-field)) "*")
        unknown-facets (cset/difference (set (map first facets)) (set facet-names))]
    (if (empty? unknown-facets)
      (pass context)
      (reduce #(fail %1 %2 :facet-not-available
                     (str "Facet " %2 " specified but there is "
                          "no such facet for this route. Valid "
                          "facets for this route are: "
                          (string/join ", " facet-names)))
              context
              unknown-facets))))

(def validate-work-facets (partial validate-facets fac/std-facets))

(defn validate-query-fields [query-field-definitions context query-fields]
  (let [available-query-fields (keys query-field-definitions)
        unknown-query-fields (cset/difference (set query-fields)
                                              (set available-query-fields))]
    (if (empty? unknown-query-fields)
      (pass context)
      (reduce #(fail %1 %2 :field-query-not-available
                     (str "Field query " %2 " specified byt there is no "
                          "such field query for this route. Valid field "
                          "queries for this route are: "
                          (string/join ", " available-query-fields)))
              context
              unknown-query-fields))))

(def validate-work-query-fields (partial validate-query-fields qf/work-fields))

(defn validate-paging-params
  [context params]
  (let [existence-checks-context
        (if (:singleton context)
          (-> context
              (validate #(not (:rows params))
                        :parameter-not-allowed
                        "This route does not support rows"
                        "rows")
              (validate #(not (:sample params))
                        :parameter-not-allowed
                        "This route does not support sample"
                        "sample")
              (validate #(not (:offset params))
                        :parameter-not-allowed
                        "This route does not support offset"
                        "offset"))
          (-> context
              (validate #(if (:sample params)
                           (not (or (:rows params) (:offset params)))
                           true)
                        :sample-with-rows-or-offset
                        "Sample cannot be combined with rows or offset"
                        "sample")))]
    (if (:deep-pagable context)
      (-> existence-checks-context
          (integer-validator (:rows params) :max q/max-rows)
          (integer-validator (:sample params) :max q/max-sample)
          (integer-validator (:offset params)
                             :max q/max-offset
                             :message " Use the cursor parameter to page further into result sets."))
      (-> existence-checks-context
          (integer-validator (:rows params) :max q/max-rows)
          (integer-validator (:sample params) :max q/max-sample)
          (integer-validator (:offset params))))))

(defn validate-cursor-params [context params]
  (if-not (:deep-pagable context)
    (validate context #(not (:cursor params))
              :parameter-not-allowed
              "This route does not support cursor"
              "cursor")
    (validate context
              #(if (:cursor params)
                 (not (or (:offset params) (:sample params)))
                 true)
              :cursor-with-offset-or-sample
              "Cursor cannot be combined with offset or sample"
              "cursor")))

(defn validate-ordering-params [context params]
  (-> context
      (util/?> (:order params) sort-order-validator (:order params))
      (util/?> (:sort params) sort-field-validator (:sort params))))

;; TODO implement, although covered by 404s in some cases
(defn validate-id [context rc]
  (if (:id-validator context)
    (pass context)
    (pass context)))

(defn parse-pair-list-form [s]
  (->> (string/split s #",")
       ;; temp fix for category names containing ','
       ;; TODO clean up category names
       (filter #(if (.startsWith % " ")
                  (nil? (re-find #":" s))
                  true))
       (map #(string/split % #":" 2))))

(defn validate-pair-list-form [context s & {:keys [also]}]
  (if (and also (some #{s} also))
    (pass context)
    (try
      (let [parsed (->> (parse-pair-list-form s)
                        (into {}))]
        (pass context))
      (catch Exception e
        (fail context s :pair-list-form-invalid
              (str "Pair list form specified as '" s "' but should be of"
                   " the form: key:val,...,keyN:valN"))))))

(defn validate-pair-list-forms [context params]
  (-> context
      (util/?> (:facet params)
               validate-pair-list-form (:facet params)
               :also wildcard-facet-forms)
      (util/?> (:filter params)
               validate-pair-list-form (:filter params))))

(defn validate-facet-param [context params]
  (if (or (:singleton context)
          (not (:facet-validator context)))
    (-> context
        (validate #(not (:facet params))
                  :parameter-not-allowed
                  "This route does not support facet"
                  "facet"))
    (cond (not (:facet params))
          context
          (some #{(:facet params)} wildcard-facet-forms)
          context
          :else
          (let [facets (try
                         (parse-pair-list-form (:facet params))
                         (catch Exception e {}))]
            ((:facet-validator context) context facets)))))

(defn validate-filter-param [context params]
  (if (or (:singleton context)
          (not (:filter-validator context)))
    (-> context
        (validate #(not (:filter params))
                  :parameter-not-allowed
                  "This route does not support filter"
                  "filter"))
    (if-not (:filter params)
      context
      (let [filters (try
                      (parse-pair-list-form (:filter params))
                      (catch Exception e {}))]
        ((:filter-validator context) context filters)))))

(defn validate-field-query-params [context params]
  (let [field-queries (q/get-field-queries params)
        allows-field-queries (and (not (:singleton context))
                                  (:query-field-validator context))
        has-field-queries (-> field-queries empty? not)]
    (cond (and allows-field-queries has-field-queries)

          ;; Field queries are allowed and provided, so validate them
          ((:query-field-validator context)
           context
           (map first field-queries))
          
          (and (not allows-field-queries) has-field-queries)

          ;; Field queries are not allowed but are provided, raise error
          (fail context "query.*" :parameter-not-allowed
                "This route does not support field query parameters")

          :else
          (pass context))))

(defn validate-query-param [context params]
  (validate context
            #(not (and (:singleton context) (:query params)))
            :parameter-not-allowed
            "This route does not support query"
            "query"))

(def available-params [:query :rows :offset :sample :facet :filter
                       :sort :order :cursor
                       :pingback :url :filename :parent :test])

;; TODO Expand validate-params and use it to replace other param checks.
;; TODO Check that deposit params only specified on POST, and not
;;      with default route params.

(defn validate-params
  "Check for unknown parameters"
  [context params]
  (let [without-query-params (->> params
                                  keys
                                  (filter #(not (.startsWith (name %) "query.")))
                                  set)
        unknown-params (cset/difference without-query-params
                                        (set available-params))]
    (if (empty? unknown-params)
      (pass context)
      (reduce #(fail %1 %2 :unknown-parameter
                     (str "Parameter " (name %2)
                          " specified but there is no such"
                          " parameter available on any route"))
                context
                unknown-params))))

(defn validate-resource-context [context resource-context]
  (let [params (p/get-parameters resource-context
                                 :query-params true 
                                 :body-params (not= :post (get-in resource-context 
                                                                  [:request :request-method])))]
    (-> context
        (validate-params params)
        (validate-pair-list-forms params)
        (validate-paging-params params)
        (validate-cursor-params params)
        (validate-ordering-params params)
        (validate-facet-param params)
        (validate-filter-param params)
        (validate-query-param params)
        (validate-field-query-params params)
        (validate-id resource-context))))

(defn malformed? [& {:keys [id-validator facet-validator filter-validator
                            query-field-validator singleton deep-pagable]
                     :or {singleton false deep-pagable false limited-offset false}}]
  (fn [resource-context]
    (let [validation-context {:id-validator id-validator
                              :facet-validator facet-validator
                              :filter-validator filter-validator
                              :query-field-validator query-field-validator
                              :singleton singleton
                              :deep-pagable deep-pagable}
          result (validate-resource-context validation-context
                                            resource-context)]
      (if (:failures result)
        {:representation {:media-type "application/json"}
         :validation-result
         {:status :failed
          :message-type :validation-failure
          :message (:failures result)}}
        false))))

