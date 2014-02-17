(ns cayenne.tasks.publisher
  (:import [java.net URI]
           [java.io FileNotFoundException])
  (:require [cayenne.item-tree :as itree]
            [cayenne.conf :as conf]
            [cayenne.util :as util]
            [cayenne.data.work :as works]
            [cayenne.util :refer [?> ?>>]]
            [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]
            [clj-time.core :as dt]
            [clj-time.format :as df]
            [clj-time.coerce :as dc]
            [somnium.congomongo :as m]))

(defn ensure-publisher-indexes! [collection-name]
  (m/add-index! collection-name [:id])
  (m/add-index! collection-name [:tokens])
  (m/add-index! collection-name [:prefixes])
  (m/add-index! collection-name [:names]))

(defn insert-publisher!
  "Upsert a publisher, combining multiple prefixes."
  [collection id prefix name location]
  (m/update! collection
             {:id id}
             {"$set" {:id id
                      :location (string/trim location)}
              "$addToSet" {:prefixes prefix
                           :tokens {"$each" (util/tokenize-name name)}
                           :names (string/trim name)}}))

(defn load-publishers [collection]
  (m/with-mongo (conf/get-service :mongo)
    (ensure-publisher-indexes! collection)
    (doseq [prefix-number (range 1000 100000)]
      (let [prefix (str "10." prefix-number)
            url (str
                 (conf/get-param [:upstream :prefix-info-url])
                 prefix)
            root (try 
                   (with-open [rdr (io/reader url)]
                     (-> rdr xml/parse zip/xml-zip))
                   (catch Exception e nil))]
        (when root
          (when-let [id (zx/xml1-> root :publisher :member_id)]
            (insert-publisher!
             collection
             (-> id zx/text (Integer/parseInt))
             prefix
             (zx/text (zx/xml1-> root :publisher :publisher_name))
             (zx/text (zx/xml1-> root :publisher :publisher_location)))))))))

(def date-format (df/formatter "yyyy-MM-dd"))

(defn back-file-cut-off []
  (df/unparse date-format (dt/minus (dt/now) (dt/years 2))))

(defn get-work-count 
  "Get a count of works for a member, with optional filters. timing
   may be one of :current, :backfile or :all."
  [member-id & {:keys [filters timing] :or {:timing :all}}]
  (let [combined-filters
        (-> {:member (str member-id)}
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

(defn make-filter-check [check-name filter-name filter-value]
  (fn [member-id]
    (let [total-count (get-work-count member-id)
          total-back-file-count (get-work-count member-id :timing :backfile)
          total-current-count (get-work-count member-id :timing :current)
          filter-back-file-count (get-work-count member-id :filters {filter-name filter-value} :timing :backfile)
          filter-current-count (get-work-count member-id :filters {filter-name filter-value} :timing :current)]
      {:flags {(keyword (str "deposits-" check-name "-current"))
               (not (zero? filter-current-count))
               (keyword (str "deposits-" check-name "-backfile"))
               (not (zero? filter-back-file-count))}
       :coverage {(keyword (str check-name "-current"))
                  (coverage total-current-count filter-current-count)
                  (keyword (str check-name "-backfile"))
                  (coverage total-back-file-count filter-back-file-count)}})))

(defn check-deposits [member-id]
  {:flags 
   {:deposits 
    (-> (get-work-count member-id)
        (zero?)
        (not))}})

(defn check-deposits-articles [member-id]
  {:flags
   {:deposits-articles
    (-> (get-work-count member-id :filters {:type "journal-article"})
        (zero?)
        (not))}})

(def checkles
  [check-deposits 
   check-deposits-articles
   (make-filter-check "licenses" :has-license "true")
   (make-filter-check "resource-links" :has-full-text "true")
   (make-filter-check "orcids" :has-orcid "true")
   (make-filter-check "funders" :has-funder "true")])

(defn check-publisher [publisher]
  (-> {:last-status-check-time (dc/to-long (dt/now))}
      (merge
       (reduce (fn [rslt chk-fn] 
                 (let [check-result (chk-fn (:id publisher))]
                   {:last-status-check-time (dc/to-long (dt/now))
                    :flags (merge (:flags rslt) (:flags check-result))
                    :coverage (merge (:coverage rslt) (:coverage check-result))}))
               {} 
               checkles))))

(defn check-publishers
  "Calculate and insert publisher/member quality metrics into a collection."
  [collection]
  (m/with-mongo (conf/get-service :mongo)
    (doseq [publisher (m/fetch collection)]
      (m/update! collection
                 publisher
                 (merge publisher (check-publisher publisher))))))
    
