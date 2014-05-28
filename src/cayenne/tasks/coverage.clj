(ns cayenne.tasks.coverage
  (:require [cayenne.ids.issn :as issn-id]
            [cayenne.conf :as conf]
            [cayenne.util :refer [?> ?>>]]
            [cayenne.data.work :as works]
            [clj-time.core :as dt]
            [clj-time.format :as df]
            [clj-time.coerce :as dc]
            [somnium.congomongo :as m]))

(def date-format (df/formatter "yyyy-MM-dd"))

(defn back-file-cut-off []
  (df/unparse date-format (dt/minus (dt/now) (dt/years 2))))

(defn make-id-filter [type id]
  (cond (= type :member)
        {:member (str id)}
        (= type :issn)
        {:issn (map issn-id/to-issn-uri id)}))

(defn get-work-count 
  "Get a count of works, with optional filters. timing may be one of :current, 
   :backfile or :all."
  [type id & {:keys [filters timing] :or {:timing :all}}]
  (let [combined-filters
        (-> (make-id-filter type id)
            (?> filters merge filters)
            (?> (= timing :current) assoc :from-pub-date (back-file-cut-off))
            (?> (= timing :backfile) assoc :until-pub-date (back-file-cut-off)))]
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
   (make-filter-check "deposits" "update-policies" :has-update-policy "true")
   (make-filter-check "deposits" "references" :has-references "true")
   (make-filter-check "deposits" "licenses" :has-license "true")
   (make-filter-check "deposits" "resource-links" :has-full-text "true")
   (make-filter-check "deposits" "orcids" :has-orcid "true")
   (make-filter-check "deposits" "funders" :has-funder "true")])

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

;; (defn check-participation [record & {:keys [type id-field]}]
;;   (let [record-id (get record id-field)
;;         works (works/fetch (merge {:rows 0 :facets ["t"]} (make-id-filter type record-id)))]
;;     {:participation
;;      {:by-deposit-year {}
;;       :by-publication-year {}}}))

(defn check-record-counts [record & {:keys [type id-field]}]
  (let [record-id (get record id-field)
        backfile-count (get-work-count type record-id :timing :backfile)
        current-count (get-work-count type record-id :timing :current)]
    {:counts
     {:backfile-dois backfile-count
      :current-dois current-count}}))

(defn check-members
  "Calculate and insert member metadata coverage metrics into a collection."
  [collection]
  (m/with-mongo (conf/get-service :mongo)
    (doseq [member (m/fetch collection :options [:notimeout])]
      (m/update! 
       collection
       member
       (merge member
              (check-record-coverage member :type :member :id-field :id)
              (check-record-counts member :type :member :id-field :id))))))

(defn check-journals
  "Calculate and insert journal metadata coverage metrics into a collection."
  [collection]
  (m/with-mongo (conf/get-service :mongo)
    (doseq [journal (m/fetch collection :options [:notimeout])]
      (m/update! 
       collection 
       journal 
       (merge journal 
              (check-record-coverage journal :type :issn :id-field :issn)
              (check-record-counts journal :type :issn :id-field :issn))))))
