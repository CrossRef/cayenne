(ns cayenne.tasks.coverage
  (:require [cayenne.ids.issn :as issn-id]
            [cayenne.conf :as conf]
            [cayenne.util :refer [?> ?>>]]
            [cayenne.data.work :as works]
            [cayenne.util :as util]
            [clj-time.core :as dt]
            [clj-time.format :as df]
            [clj-time.coerce :as dc]
            [taoensso.timbre :as timbre :refer [error]]
            [somnium.congomongo :as m]))

(def date-format (df/formatter "yyyy-MM-dd"))

(defn back-file-cut-off []
  (df/unparse date-format (dt/minus (dt/now) (dt/years 2))))

(defn make-id-filter [type id]
  (cond (= type :member)
        {:member (str id)}
        (= type :issn)
        {:issn (map issn-id/to-issn-uri id)}
        (= type :doi)
        {:doi (str id)}))

(defn get-types
  "Get types for member"
  [id filters]
  (let [combined-filters (merge { :member (str id)} filters)]
    ;(println "filters: " (assoc {:rows (int 0) :facets [{:field "type-name" :count "*" }]} :filters combined-filters))
    (-> (assoc {:rows (int 0) :facets [{:field "type-name" :count "*" }]} :filters combined-filters)
        (works/fetch)
        (get-in [:message :facets "type-name" :values]))))

(defn get-work-count
  "Get a count of works, with optional filters. timing may be one of :current,
   :backfile or :all."
  [type id & {:keys [filters timing] :or {:timing :all}}]
  ;(println "plain filters: " filters)
  (let [combined-filters
        (-> (make-id-filter type id)
            (?> filters merge filters)
            (?> (= timing :current) assoc :from-pub-date (back-file-cut-off))
            (?> (= timing :backfile) assoc :until-pub-date (back-file-cut-off)))]
    ;(println "combined filters: " combined-filters)
    (-> (assoc {:rows (int 0)} :filters combined-filters)
        (works/fetch)
        (get-in [:message :total-results]))))

(defn coverage [total-count check-count]
  (if (zero? total-count)
    0
    (float (/ check-count total-count))))

(defn make-filter-check [member-action check-name filter-name filter-value]
  (fn [type id]
    (let [total-count (get-work-count type id)
          total-back-file-count (get-work-count type id :timing :backfile)
          total-current-count (get-work-count type id :timing :current)
          filter-back-file-count (get-work-count type id :filters {filter-name filter-value} :timing :backfile)
          filter-current-count (get-work-count type id :filters {filter-name filter-value} :timing :current)]
      {:flags {(keyword (str member-action "-" check-name "-current"))
               (not (zero? filter-current-count))
               (keyword (str member-action "-" check-name "-backfile"))
               (not (zero? filter-back-file-count))}
       :coverage {(keyword (str check-name "-current"))
                  (coverage total-current-count filter-current-count)
                  (keyword (str check-name "-backfile"))
                  (coverage total-back-file-count filter-back-file-count)}})))

(defn make-filter-check-for-type [member-action check-name filter-name filter-value]
  (fn [type id total timing]
      (let
        [filter-current-count (get-work-count :member id :filters {filter-name filter-value "type-name" type :timing timing})
         result  {:coverage {(keyword  check-name)
                             (coverage total filter-current-count)}}]
        result)))

(defn make-filter-check-new [member-action check-name filter-name filter-value]
  (fn [id timing total]
      (let
        [
         filter-current-count (get-work-count :issn id :filters {filter-name filter-value :timing timing})
         result  {:coverage {(keyword  check-name)
                             (coverage total filter-current-count)}}]
        ;(println "filter-current-count: " filter-current-count)
        ;(println "total: " total " timing: " timing " id:" id " result: " result)
        result)))


(defn check-deposits [type id]
  {:flags
   {:deposits
    (-> (get-work-count type id)
        (zero?)
        (not))}})

(defn check-deposits-articles [type id]
  {:flags
   {:deposits-articles
    (-> (get-work-count type id :filters {:type "journal-article"})
        (zero?)
        (not))}})

(def checkles
  [check-deposits
   check-deposits-articles
   (make-filter-check "deposits" "affiliations" :has-affiliation "true")
   (make-filter-check "deposits" "abstracts" :has-abstract "true")
   (make-filter-check "deposits" "update-policies" :has-update-policy "true")
   (make-filter-check "deposits" "references" :has-references "true")
   (make-filter-check "deposits" "licenses" :has-license "true")
   (make-filter-check "deposits" "resource-links" :has-full-text "true")
   (make-filter-check "deposits" "orcids" :has-orcid "true")
   (make-filter-check "deposits" "award-numbers" :has-award "true")
   (make-filter-check "deposits" "funders" :has-funder "true")
   (make-filter-check "deposits" "open-references" :reference-visibility "open")
   (make-filter-check "deposits" "similarity-checking" :full-text {"application" ["similarity-checking"]})])

(def checklesnew
  [;check-deposits
   ;check-deposits-articles
   (make-filter-check-new "deposits" "affiliations" :has-affiliation "true")
   (make-filter-check-new "deposits" "abstracts" :has-abstract "true")
   (make-filter-check-new "deposits" "update-policies" :has-update-policy "true")
   (make-filter-check-new "deposits" "references" :has-references "true")
   (make-filter-check-new "deposits" "licenses" :has-license "true")
   (make-filter-check-new "deposits" "resource-links" :has-full-text "true")
   (make-filter-check-new "deposits" "orcids" :has-orcid "true")
   (make-filter-check-new "deposits" "award-numbers" :has-award "true")
   (make-filter-check-new "deposits" "funders" :has-funder "true")
   (make-filter-check-new "deposits" "open-references" :reference-visibility "open")
   (make-filter-check "deposits" "similarity-checking" :full-text {"application" ["similarity-checking"]})])

(def checklestype
  [(make-filter-check-for-type "deposits" "affiliations" :has-affiliation "true")
   (make-filter-check-for-type "deposits" "abstracts" :has-abstract "true")
   (make-filter-check-for-type "deposits" "update-policies" :has-update-policy "true")
   (make-filter-check-for-type "deposits" "references" :has-references "true")
   (make-filter-check-for-type "deposits" "licenses" :has-license "true")
   (make-filter-check-for-type "deposits" "resource-links" :has-full-text "true")
   (make-filter-check-for-type "deposits" "orcids" :has-orcid "true")
   (make-filter-check-for-type "deposits" "award-numbers" :has-award "true")
   (make-filter-check-for-type "deposits" "funders" :has-funder "true")
   (make-filter-check-for-type "deposits" "open-references" :reference-visibility "open")
   (make-filter-check "deposits" "similarity-checking" :full-text {"application" ["similarity-checking"]})])

(defn check-record-coverage [record & {:keys [type id-field]}]
  (-> {:last-status-check-time (dc/to-long (dt/now))}
      (merge
       (reduce (fn [rslt chk-fn]
                 (let [check-result (chk-fn type (get record id-field))]
                   {:last-status-check-time (dc/to-long (dt/now))
                    :flags (merge (:flags rslt) (:flags check-result))
                    :coverage (merge (:coverage rslt) (:coverage check-result))}))
               {}
               checkles))))
(defn check-record-coverage-per-type [record types & {:keys [timing id-field] :or {:timing :all}}]
  ;(println "doing per type for types:" types)
  (if (> (count types) 0)
      (let [reslt (map (fn [[typestr valnum]]
                        ;(println "doing per type:" typestr " val: " valnum)
                        (hash-map (keyword typestr) (-> {:last-status-check-time (dc/to-long (dt/now))}
                                                      (merge
                                                        (reduce (fn [rslt chk-fn]
                                                                  (let [check-result (chk-fn typestr (get record id-field) valnum timing)
                                                                        content {:last-status-check-time (dc/to-long (dt/now))
                                                                                 :total valnum}]
                                                                       ;(println "coverage result:" (:coverage rslt))
                                                                       ;(println "check result:" (:coverage check-result))

                                                                     ;:flags (merge (:flags rslt) (:flags check-result))
                                                                     (merge content  rslt (:coverage check-result))))
                                                            {}
                                                            checklestype)))))

                    types)]
        ;(println "got coverage for member: " (get record id-field))
        ;(println  (into {} reslt))
        (into {} reslt))

    (println "No types for member: " (get record id-field) " timing: " timing)))

(defn check-record-coverage-new [record & {:keys [timing id-field] :or {:timing :all}}]
  (let [totalc (get-work-count :issn (get record id-field) :timing timing)]
    (-> {:last-status-check-time (dc/to-long (dt/now))}
        (merge
         (reduce (fn [rslt chk-fn]
                   (let [check-result (chk-fn  (get record id-field) timing totalc)
                         content {:last-status-check-time (dc/to-long (dt/now))
                                  :total totalc}]
                        ;(println "check result:" content)
                        ;(println "check result:" (:coverage check-result))
                        (merge content  rslt (:coverage check-result))))

                 {}
                 checklesnew)))))

(defn check-breakdowns [record & {:keys [type id-field]}]
  (let [record-id (get record id-field)
        works (works/fetch {:rows (int 0)
                            :facets [{:field "published" :count "*"}]
                            :filters (make-id-filter type record-id)})]
    {:breakdowns
     {:dois-by-issued-year
      (reduce
              (fn [a [k v]] (conj a [(util/parse-int k) v]))
              []
              (-> works
                  (get-in [:message :facets "published" :values])))}}))

(defn check-record-counts [record & {:keys [type id-field]}]
  (let [record-id (get record id-field)
        backfile-count (get-work-count type record-id :timing :backfile)
        current-count (get-work-count type record-id :timing :current)]
    {:counts
     {:backfile-dois backfile-count
      :current-dois current-count
      :total-dois (+ backfile-count current-count)}}))
(defn checks-for-timespans
  [member]
  (let [current  {:current (check-record-coverage-per-type member (get-types (:id member) {:from-pub-date (back-file-cut-off)}) :timing :current :id-field :id)}
        backfile {:backfile (check-record-coverage-per-type member (get-types (:id member) {:to-pub-date (back-file-cut-off)}) :timing :backfile :id-field :id)}
        all  {:all (check-record-coverage-per-type member (get-types (:id member) nil) :id-field :id)}
        result {:coverage-type (merge  current backfile all)}]
    ;(println result)
    result))
(defn checks-for-timespans-journals
  [journal]
  (let [current  {:current (check-record-coverage-new journal :id-field :issn :timing :current)}
        backfile {:backfile (check-record-coverage-new journal :id-field :issn  :timing :backfile)}
        all  {:all (check-record-coverage-new journal :id-field :issn)}
        result {:coverage-type (merge  current backfile all)}]
    ;(println result)
    result))
(defn check-members
  "Calculate and insert member metadata coverage metrics into a collection."
  [collection]
  (println "start check members:" (dc/to-long (dt/now)))
  (m/with-mongo (conf/get-service :mongo)
    (doseq [member  (m/fetch collection :options [:notimeout])]
        ;(doall (map (fn [[key val]] (println "in check members key:" key "val:" val) ) (get-types (:id member))))
      ;(println (merge member (checks-for-timespans member)))
      ;(println (merge member {:all (check-record-coverage-per-type member (get-types (:id member)) :id-field :id)})
        (try
          (m/update!
           collection
           {:id (:id member)}
           (merge member
                  (check-breakdowns member :type :member :id-field :id)
                  (checks-for-timespans member)
                  (check-record-coverage member :type :member :id-field :id)
                  (check-record-counts member :type :member :id-field :id)))
          (catch Exception e
            (error e "Failed to update coverage for member with ID " (:id member))
            (.printStackTrace e)))))
  (println "end check members:" (dc/to-long (dt/now))))


(defn check-journals
  "Calculate and insert journal metadata coverage metrics into a collection. Only consider
   journals that have an ISSN."
  [collection]
  (println "start check journals:" (dc/to-long (dt/now)))
  (m/with-mongo (conf/get-service :mongo)
    (doseq [journal (m/fetch collection :where {:issn {:$exists true :$ne []} :doi {:$eq ""}} :options [:notimeout])]
      ;(println "checksforjournal: " (:issn journal) (checks-for-timespans-journals journal))
      (try
        (m/update!
         collection
         {:id (:id journal)}
         (merge journal
                (check-breakdowns journal :type :issn :id-field :issn)
                (checks-for-timespans-journals journal)
                (check-record-coverage journal :type :issn :id-field :issn)
                (check-record-counts journal :type :issn :id-field :issn)))
        (catch Exception e
          (error e "Failed to update coverage for journal with ID " (:id journal)))))

    (println "end check journals:" (dc/to-long (dt/now)))))
