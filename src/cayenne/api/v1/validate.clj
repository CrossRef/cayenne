(ns cayenne.api.v1.validate
  (:require [clojure.string :as string]
            [clojure.set :as cset]
            [cayenne.util :as util]
            [cayenne.api.v1.filter :as f]
            [cayenne.api.v1.parameters :as p]
            [cayenne.api.v1.facet :as fac]
            [cayenne.api.v1.query :as q]
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

(defn integer-validator [context s & {:keys [max] :or {max :infinite}}]
  (if (or (not s)
          (and (re-matches #"\d+" s)
               (if (= max :infinite) true (<= (util/parse-int-safe s) max))))
    (pass context)
    (fail context s :integer-not-valid
          (str "Integer specified as " s " but must be a positive integer"
               (when (not= max :infinite)
                 (str " less than or equal to " max))))))

(defn date-validator [context s]
  (if (re-matches #"(\d{4})|(\d{4}-\d{2})|(\d{4}-\d{2}-\d{2})" s)
    (pass context)
    (fail context s :date-not-valid
          (str "Date specified as " s " but must be of the form: "
               "yyyy or yyyy-MM or yyyy-MM-dd"))))

;; TODO date time
(defn datetime-validator [context s]
  (date-validator context s))

(defn content-type-validator [context s]
  (if (re-matches #"[\s-]+/[\s-+.]+" s)
    (pass context)
    (fail context s :content-type-not-valid
          (str "Content type specified as " s " but must be "
               " a valid MIME type without wildcards"))))

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
   :is-update boolean-validator
   :has-update boolean-validator
   :updates doi-validator
   :update-type string-validator
   :has-full-text boolean-validator
   :has-license boolean-validator
   :has-references boolean-validator
   :has-update-policy boolean-validator
   :has-archive boolean-validator
   :has-orcid boolean-validator
   :has-funder boolean-validator
   :has-funder-doi boolean-validator
   :has-award boolean-validator
   :directory directory-validator
   :archive archive-validator
   :issn issn-validator
   :type type-validator
   :type-name string-validator
   :orcid orcid-validator
   :doi doi-validator
   :container-title string-validator
   :publisher-name string-validator
   :category-name string-validator
   :funder-name string-validator
   :member integer-validator
   :prefix prefix-validator
   :funder funder-id-validator
   :full-text.type content-type-validator
   :full-text.application intended-application-validator
   :full-text.version version-validator
   :license.url url-validator
   :license.version version-validator
   :license.delay integer-validator
   :award.funder funder-id-validator
   :award.number string-validator})

(def deposit-filter-validators
  {:from-submission-time datetime-validator
   :until-submission-time datetime-validator
   :status deposit-status-validator
   :owner string-validator
   :type content-type-validator
   :doi doi-validator
   :test boolean-validator})

(def member-filter-validators
  {:prefix prefix-validator
   :backfile-doi-count integer-validator
   :currnet-doi-count integer-validator})

(defn validate-filters [filter-definitions filter-validators context filters]
  (let [unknown-filters (cset/difference
                         (set (map first filters))
                         (set (keys filter-definitions)))
        existence-chk-context 
        (if (empty? unknown-filters)
          (pass context)
          (reduce #(fail %1 %2 :filter-not-available
                         (str "Filter " %2 " specified but there "
                              "is no such filter for this route."
                              " Valid filters for this route are: "
                              (string/join ", " (keys filter-definitions))))
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
                                       f/deposit-filters
                                       deposit-filter-validators))
(def validate-work-filters (partial validate-filters
                                    f/std-filters
                                    work-filter-validators))
(def validate-member-filters (partial validate-filters
                                      f/member-filters
                                      member-filter-validators))

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
    (-> existence-checks-context
        (integer-validator (:rows params) :max q/max-rows)
        (integer-validator (:sample params))
        (integer-validator (:offset params)))))

(defn validate-ordering-params [context params]
  (-> context
      (util/?> (:order params) sort-order-validator (:order params))
      (util/?> (:sort params) sort-field-validator (:sort params))))

;; TODO implement, although covered by 404s in some cases
(defn validate-id [context rc]
  (if (:id-validator context)
    (pass context)
    (pass context)))

(defn validate-pair-list-form [context s & {:keys [also]}]
  (if (and also (some #{s} also))
    (pass context)
    (try
      (let [parsed (->> (string/split s #",")
                        (map #(string/split % #":"))
                        (into {}))]
        (pass context))
      (catch Exception e
        (fail context s :pair-list-form-invalid
              (str "Pair list form specified as '" s "' but should be of"
                   " the form: key:val,...,keyN:valN"))))))

(defn validate-pair-list-forms [context params]
  (-> context
      (util/?> (:facet params)
               validate-pair-list-form (:facet params) :also wildcard-facet-forms)
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
                         (map #(string/split % #":")
                              (-> (:facet params)
                                  (string/split #",")))
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
                      (map #(string/split % #":")
                           (-> (:filter params)
                               (string/split #",")))
                      (catch Exception e {}))]
        ((:filter-validator context) context filters)))))

(defn validate-query-param [context params]
  (validate context
            #(not (and (:singleton context) (:query params)))
            :parameter-not-allowed
            "This route does not support query"
            "query"))

(def available-params [:query :rows :offset :sample :facet :filter
                       :pingback :url :filename :parent :test])

;; TODO Expand validate-params and use it to replace other param checks.
;; TODO Check that deposit params only specified on POST, and not
;;      with default route params.

(defn validate-params
  "Check for unknown parameters"
  [context params]
  (println (keys params))
  (let [unknown-params (cset/difference (set (keys params)) (set available-params))]
    (if (empty? unknown-params)
      (pass context)
      (reduce #(fail %1 %2 :unknown-parameter
                     (str "Parameter " (name %2) " specified but there is no such"
                          " parameter available on any route"))
                context
                unknown-params))))

(defn validate-resource-context [context resource-context]
  (let [params (p/get-parameters resource-context)]
    (-> context
        (validate-params params)
        (validate-pair-list-forms params)
        (validate-paging-params params)
        (validate-ordering-params params)
        (validate-facet-param params)
        (validate-filter-param params)
        (validate-query-param params)
        (validate-id resource-context))))

(defn malformed? [& {:keys [id-validator facet-validator filter-validator singleton]
                     :or {singleton false}}]
  (fn [resource-context]
    (let [validation-context {:id-validator id-validator
                              :facet-validator facet-validator
                              :filter-validator filter-validator
                              :singleton singleton}
          result (validate-resource-context validation-context
                                            resource-context)]
      (if (:failures result)
        {:representation {:media-type "application/json"}
         :validation-result
         {:status :failed
          :message-type :validation-failure
          :message (:failures result)}}
        false))))
