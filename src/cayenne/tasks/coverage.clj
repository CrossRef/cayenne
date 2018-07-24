(ns cayenne.tasks.coverage
  (:require [cayenne.ids.issn :as issn-id]
            [cayenne.ids.type :as type-id]
            [cayenne.conf :as conf]
            [cayenne.util :refer [?> ?>>]]
            [cayenne.data.work :as works]
            [cayenne.util :as util]
            [clj-time.core :as dt]
            [clj-time.format :as df]
            [clj-time.coerce :as dc]
            [taoensso.timbre :as timbre :refer [error]]
            [qbits.spandex :as elastic]
            [cayenne.elastic.util :as elastic-util])
  (:import [java.util UUID]))

(def date-format (df/formatter "yyyy-MM-dd"))

(defn back-file-cut-off []
  (df/unparse date-format (dt/minus (dt/now) (dt/years 2))))

(defn make-id-filter [type id]
  (cond (= type :member)
        {:member [(str id)]}
        (= type :journal)
        {:journal [(str id)]}))

(defn get-work-count
  "Get a count of works, with optional filters. timing may be one of :current, 
   :backfile or :all."
  [type id & {:keys [filters timing] :or {:timing :all}}]
  (let [combined-filters
        (-> (make-id-filter type id)
            (?> filters merge filters)
            (?> (= timing :current) assoc :from-pub-date [(back-file-cut-off)])
            (?> (= timing :backfile) assoc :until-pub-date [(back-file-cut-off)]))]
    (-> (assoc {:rows (int 0)} :filters combined-filters)
        (works/fetch)
        (get-in [:message :total-results]))))

(defn coverage [total-count check-count]
  (if (zero? total-count)
    0.0
    (double (/ check-count total-count))))

(defn check-deposits [type id]
  {:flags
   {:deposits
    (-> (get-work-count type id)
        (zero?)
        (not))}})

(defn check-deposits-articles [type id]
  {:flags
   {:deposits-articles
    (-> (get-work-count type id :filters {:type ["journal-article"]})
        (zero?)
        (not))}})

(def checkles
  [{:name "affiliations"        :filter {:has-affiliation       ["true"]}}
   {:name "abstracts"           :filter {:has-abstract          ["true"]}}
   {:name "update-policies"     :filter {:has-update-policy     ["true"]}}
   {:name "references"          :filter {:has-references        ["true"]}}
   {:name "licenses"            :filter {:has-license           ["true"]}}
   {:name "resource-links"      :filter {:has-full-text         ["true"]}}
   {:name "orcids"              :filter {:has-orcid             ["true"]}}
   {:name "award-numbers"       :filter {:has-award             ["true"]}}
   {:name "funders"             :filter {:has-funder            ["true"]}}
   {:name "open-references"     :filter {:reference-visibility   ["open"]}}
   {:name "similarity-checking" :filter {:full-text [[:application ["similarity-checking"]]]}}])

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
    {:backfile-dois backfile-count
     :current-dois current-count
     :total-dois (+ backfile-count current-count)}))

(defn get-check-counts [record-id type content-type]
  (let [filters (if (= content-type :all) {} {:type [(name content-type)]})
        total-backfile-counts (get-work-count type record-id :timing :backfile :filters filters)
        total-current-counts (get-work-count type record-id :timing :current :filters filters)]
    (reduce
     (fn [m v]
       (let [check-backfile-counts (get-work-count type record-id :timing :backfile :filters (merge filters (:filter v)))
             check-current-counts (get-work-count type record-id :timing :current :filters (merge filters (:filter v)))]
         (-> m
             (assoc-in [:backfile content-type (keyword (str (:name v)))] (coverage total-backfile-counts check-backfile-counts))
             (assoc-in [:current content-type (keyword (str (:name v)))] (coverage total-current-counts check-current-counts))
             (assoc-in [:all content-type (keyword (str (:name v)))] (coverage (+ total-current-counts total-backfile-counts) (+ check-backfile-counts check-current-counts))))))
     {:all {content-type {:_count (+ total-backfile-counts total-current-counts)}}
      :current {content-type {:_count total-current-counts}}
      :backfile {content-type {:_count total-backfile-counts}}}
     checkles)))

(defn check-type-coverage [record & {:keys [type id-field]}]
  (let [record-id (get record id-field)]
    (apply
     merge-with
     merge
     (map (partial get-check-counts record-id type) (conj (keys type-id/type-dictionary) :all)))))

(defn index-coverage-command [record & {:keys [type id-field]}]
  (let [started-date (dt/now)
        record-source (:_source record)
        record-counts (check-record-counts record-source :type type :id-field id-field)
        coverage (check-type-coverage record-source :type type :id-field id-field)
        breakdowns (check-breakdowns record-source :type type :id-field id-field)]
    [{:index {:_id (.toString (UUID/randomUUID))}}
     {:subject-type  (name type)
      :subject-id    (get record-source id-field)
      :started       started-date
      :finished      (dt/now)
      :total-dois    (:total-dois record-counts)
      :backfile-dois (:backfile-dois record-counts)
      :current-dois  (:current-dois record-counts)
      :breakdowns    breakdowns
      :coverage      coverage}]))

;; todo use scroll
(defn check-index [index-name id-field]
  (doseq [some-records
          (as->
           (elastic/request
            (conf/get-service :elastic)
            {:method :get
             :url (str "/" (name index-name) "/" (name index-name) "/_search")
             :body {:_source [id-field] :query {:match_all {}} :size 10000}})
           $
            (get-in $ [:body :hits :hits])
            (partition-all 100 $))]
    (elastic/request
     (conf/get-service :elastic)
     {:method :post
      :url "/coverage/coverage/_bulk"
      :body (->> some-records
                 (map #(index-coverage-command % :type index-name :id-field id-field))
                 flatten
                 elastic-util/raw-jsons)})))

(defn check-members [] (check-index :member :id))

(defn check-journals [] (check-index :journal :id))
