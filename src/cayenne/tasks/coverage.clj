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
            [somnium.congomongo :as m]
            [clojure.tools.logging :as log]
            [clojure.set :as s]
            [cayenne.ids.type :refer [->type-id reverse-dictionary]] ))

(def year-date-format (df/formatter "yyyy"))

(defn back-file-cut-off []
  (df/unparse year-date-format (dt/minus (dt/now) (dt/years 3))))

(defn current-start-year []
  (df/unparse year-date-format (dt/minus (dt/now) (dt/years 2))))

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
    (-> (assoc {:rows (int 0) :facets [{:field "type-name" :count "*" }]} :filters combined-filters)
        (works/fetch)
        (get-in [:message :facets "type-name" :values]))))

(defn get-work-count
  "Get a count of works, with optional filters. timing may be one of :current,
   :backfile or :all."
  [type id & {:keys [filters timing] :or {:timing :all}}]
  (let [combined-filters
        (-> (make-id-filter type id)
            (?> filters merge filters)
            (?> (= timing :current) assoc :from-pub-date (current-start-year))
            (?> (= timing :backfile) assoc :until-pub-date (back-file-cut-off)))]
    (-> (assoc {:rows (int 0)} :filters combined-filters)
        (works/fetch)
        (get-in [:message :total-results]))))

(defn coverage [total-count check-count]
  (when (> check-count total-count)
    (log/warn "Alert! More check count:" check-count "than total:" total-count))

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

(defn make-check-for-type 
   "makes a function that will create a check with the given name retrieving
    document counts based on given filter name and value. Created function requires
    the type of publication, member id, the timing (backfile,current nil=all) and the
    total count for that timing and type"
  [member-action check-name filter-name filter-value]
  (fn [type id total timing]
      (let
        [filter-current-count (get-work-count :member id :filters {filter-name filter-value "type-name" type} :timing timing)
         result {:coverage {(keyword check-name)
                             (coverage total filter-current-count)}}]
        result)))

(defn make-check-for-journal 
 "makes a function that will create a check with the given name retrieving
  document counts based on given filter name and value. Created function requires
  the journal id as ISSN, the timing (backfile,current nil=all) and the total count
  for that timing and journal"
  [member-action check-name filter-name filter-value]
  (fn [id timing total]
    (let
      [filter-current-count (get-work-count :issn id :filters {filter-name filter-value} :timing timing)
        result {:coverage {(keyword check-name)
                            (coverage total filter-current-count)}}]
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
   ; the compound field "full-text" has a sub field of "application". Specifying more than one application
   ; requires passing a sequence to the query generator as the value of the compound field.
   ; it will OR the key/values
   (make-filter-check "deposits" "resource-links" :full-text [{"application" ["unspecified"]} {"application" ["text-mining"]}])
   (make-filter-check "deposits" "orcids" :has-orcid "true")
   (make-filter-check "deposits" "award-numbers" :has-award "true")
   (make-filter-check "deposits" "funders" :has-funder "true")
   (make-filter-check "deposits" "open-references" :reference-visibility "open")
   (make-filter-check "deposits" "similarity-checking" :full-text {"application" ["similarity-checking"]})])

(def checkles-for-journals
  [(make-check-for-journal "deposits" "affiliations" :has-affiliation "true")
   (make-check-for-journal "deposits" "abstracts" :has-abstract "true")
   (make-check-for-journal "deposits" "update-policies" :has-update-policy "true")
   (make-check-for-journal "deposits" "references" :has-references "true")
   (make-check-for-journal "deposits" "licenses" :has-license "true")
   (make-check-for-journal "deposits" "resource-links" :full-text [{"application" ["unspecified"]} {"application" ["text-mining"]}])
   (make-check-for-journal "deposits" "orcids" :has-orcid "true")
   (make-check-for-journal "deposits" "award-numbers" :has-award "true")
   (make-check-for-journal "deposits" "funders" :has-funder "true")
   (make-check-for-journal "deposits" "open-references" :reference-visibility "open")
   (make-check-for-journal "deposits" "similarity-checking" :full-text {"application" ["similarity-checking"]})])

(def checkles-by-type
  [(make-check-for-type "deposits" "affiliations" :has-affiliation "true")
   (make-check-for-type "deposits" "abstracts" :has-abstract "true")
   (make-check-for-type "deposits" "update-policies" :has-update-policy "true")
   (make-check-for-type "deposits" "references" :has-references "true")
   (make-check-for-type "deposits" "licenses" :has-license "true")
   (make-check-for-type "deposits" "resource-links" :full-text [{"application" ["unspecified"]} {"application" ["text-mining"]}])
   (make-check-for-type "deposits" "orcids" :has-orcid "true")
   (make-check-for-type "deposits" "award-numbers" :has-award "true")
   (make-check-for-type "deposits" "funders" :has-funder "true")
   (make-check-for-type "deposits" "open-references" :reference-visibility "open")
   (make-check-for-type "deposits" "similarity-checking" :full-text {"application" ["similarity-checking"]})])

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
(defn check-record-coverage-per-type 
  "Makes a map of each given type containing each check defined in checkles-by-type,
   within the given timing"
  [record types & {:keys [timing id-field] :or {:timing :all}}]
  (if (> (count types) 0)
      (let
        [result (map (fn [[type-str val-num]]
                      (hash-map
                       (->type-id type-str)
                       (-> {:last-status-check-time (dc/to-long (dt/now))}
                         (merge
                           (reduce
                             (fn [rslt chk-fn]
                               (let [check-result (chk-fn type-str (get record id-field) val-num timing)
                                     content {:last-status-check-time (dc/to-long (dt/now))}]
                                    (merge content rslt (:coverage check-result))))
                             {}
                             checkles-by-type)))))
                  types)]
        (into {} result))
    (log/info "No types for member: " (get record id-field) " timing: " timing)))

(defn check-record-coverage-type-for-journal 
    "Makes a map of given journal containing each check defined in checkles-for-journals,
     within the given timing"
  [record & {:keys [timing id-field] :or {:timing :all}}]
  (let [totalc (get-work-count :issn (get record id-field) :timing timing)]
    (-> {:last-status-check-time (dc/to-long (dt/now))} ; probably don't want this
        (merge
          (reduce (fn [rslt chk-fn]
                  (let [check-result (chk-fn (get record id-field) timing totalc)
                        content {:last-status-check-time (dc/to-long (dt/now))}]
                     (merge content rslt (:coverage check-result))))
             {}
             checkles-for-journals)))))

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
  "calls check-record-coverage-per-type for each of the 3 time spans and populates a map
   with a key of :coverage-type and value of the merged result"
  [member]
  (let [current-types (get-types (:id member) {:from-pub-date (current-start-year)})
        backfile-types (get-types (:id member) {:until-pub-date (back-file-cut-off)})
        all-types (get-types (:id member) nil)
        current {:current (check-record-coverage-per-type member current-types :timing :current :id-field :id)}
        backfile {:backfile (check-record-coverage-per-type member backfile-types :timing :backfile :id-field :id)}
        all {:all (check-record-coverage-per-type member all-types :id-field :id)}
        result {:coverage-type (merge current backfile all)}]
    result))


(defn check-type-counts 
  "generates the counts-type map for the given member id.
   Best to run this immediately before (checks-for-timespans) to take
   advantage of caching"
  [memberid]
  (let [current-types (get-types memberid {:from-pub-date (current-start-year)})
        backfile-types (get-types memberid {:until-pub-date (back-file-cut-off)})
        all-types (get-types memberid nil)

    result {:counts-type
     {:backfile (s/rename-keys backfile-types reverse-dictionary)
      :current (s/rename-keys current-types reverse-dictionary)
      :all (s/rename-keys all-types reverse-dictionary)
      }}]
    result))

(defn checks-for-timespans-journals
  "calls check-record-coverage-type-for-journal for each of the 3 time spans and populates a map
   with a key of :coverage-type and value of the merged result"
  [journal]
  (let [current {:current (check-record-coverage-type-for-journal journal :id-field :issn :timing :current)}
        backfile {:backfile (check-record-coverage-type-for-journal journal :id-field :issn :timing :backfile)}
        all {:all (check-record-coverage-type-for-journal journal :id-field :issn)}
        result {:coverage-type (merge current backfile all)}]
    result))

(defn check-members
  "Calculate and insert member metadata coverage metrics into a collection."
  [collection]
  (log/info "start check members:" (dc/to-long (dt/now)))
  (m/with-mongo (conf/get-service :mongo)
    (doseq [member (m/fetch collection :sort {:id 1} :options [:notimeout])]
      (log/info "doing member:" (:id member) "for collection " collection)
        (try
          (m/update!
           collection
           {:id (:id member)}
           (merge member
                  (check-breakdowns member :type :member :id-field :id)
                  (check-type-counts (:id member))
                  (checks-for-timespans member)
                  (check-record-coverage member :type :member :id-field :id)
                  (check-record-counts member :type :member :id-field :id)))
          (catch Exception e
            (error e "Failed to update coverage for member with ID " (:id member))
            (.printStackTrace e)))))
  (log/info "end check members:" (dc/to-long (dt/now))))


(defn check-journals
  "Calculate and insert journal metadata coverage metrics into a collection. Only consider
   journals that have an ISSN."
  [collection]
  (let [start (dc/to-long (dt/now))]
    (log/info "start check journals:" start)
    (m/with-mongo (conf/get-service :mongo)
      (doseq [journal (m/fetch collection :where {:issn {:$exists true :$ne []}} :options [:notimeout])]
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
      (log/info "start check journals:" start)
      (log/info "end check journals:" (dc/to-long (dt/now))))))
