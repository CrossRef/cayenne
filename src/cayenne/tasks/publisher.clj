(ns cayenne.tasks.publisher
  (:import [java.net URI]
           [java.io FileNotFoundException])
  (:require [cayenne.item-tree :as itree]
            [cayenne.conf :as conf]
            [cayenne.util :as util]
            [cayenne.data.work :as works]
            [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]
            [somnium.congomongo :as m]))

(defn ensure-publisher-indexes! [collection-name]
  (m/with-mongo (conf/get-service :mongo)
    (m/add-index! collection-name [:id])
    (m/add-index! collection-name [:tokens])
    (m/add-index! collection-name [:prefixes])
    (m/add-index! collection-name [:names])))

(defn insert-publisher!
  "Upsert a publisher, combining multiple prefixes."
  [collection id prefix name location]
  (m/with-mongo (conf/get-service :mongo)
    (m/update! collection
               {:id id}
               {"$set" {:id id
                        :location (string/trim location)}
                "$addToSet" {:prefixes prefix
                             :tokens {"$each" (util/tokenize-name name)}
                             :names (string/trim name)}})))

(defn load-publishers [collection]
  (ensure-publisher-indexes! collection)
  (doseq [prefix-number (range 1000 100000)]
    (let [prefix (str "10." prefix-number)
          url (str
               (conf/get-param [:upstream :prefix-info-url])
               prefix)
          root (try
                 (-> url io/reader xml/parse zip/xml-zip)
                 (catch Exception e nil))]
      (when root
        (when-let [id (zx/xml1-> root :publisher :member_id)]
          (insert-publisher!
           collection
           (-> id zx/text (Integer/parseInt))
           prefix
           (zx/text (zx/xml1-> root :publisher :publisher_name))
           (zx/text (zx/xml1-> root :publisher :publisher_location))))))))

;; has deposited?
;; has deposited articles?
;; has deposited funder info for articles?
   ;; %, rate-100, rate-1000
;; has deposited resource links for articles?
   ;; %, rate-100, rate-1000
;; has deposited licenses for articles?
   ;; %, rate-100, rate-1000
;; has deposited citations for articles?
   ;; %, rate-100, rate-1000

; {:flags {:deposits true
;           :deposits-articles true
;           :deposits-funders true
;  :coverage {:fundings 0.14
;              :resource-links 0.6
;              :licenses 0.1} 

(defn get-work-count [member-id & filters]
  (let [optional-filters (first filters)
        fs (if optional-filters
             (merge {:member (str member-id)} optional-filters)
             {:member (str member-id)})]
    (-> (assoc {:rows (int 0)} :filters fs)
        (works/fetch)
        (get-in [:message :total-results]))))

(defn coverage [total-count check-count]
  (if (zero? total-count)
    0
    (float (/ check-count total-count))))

(defn check-deposits [member-id]
  {:flags 
   {:deposits 
    (-> (get-work-count member-id)
        (zero?)
        (not))}})

(defn check-deposits-articles [member-id]
  {:flags
   {:deposits-articles
    (-> (get-work-count member-id {:type "journal-article"})
        (zero?)
        (not))}})

(defn check-deposits-licenses [member-id]
  (let [total-count (get-work-count member-id)
        with-license-count (get-work-count member-id {:has-license "true"})]
    {:flags {:deposits-licenses (not (zero? with-license-count))}
     :coverage {:licenses (coverage total-count with-license-count)}}))

(defn check-deposits-resource-links [member-id]
  (let [total-count (get-work-count member-id)
        with-resource-links-count (get-work-count member-id {:has-full-text "true"})]
    {:flags {:deposits-resource-links (not (zero? with-resource-links-count))}
     :coverage {:resource-links (coverage total-count with-resource-links-count)}}))

(defn check-deposits-orcids [member-id]
  (let [total-count (get-work-count member-id)
        with-orcids-count (get-work-count member-id {:has-orcid "true"})]
    {:flags {:deposits-orcids (not (zero? with-orcids-count))}
     :coverage {:orcids (coverage total-count with-orcids-count)}}))

(def checkles
  [check-deposits check-deposits-articles check-deposits-licenses
   check-deposits-resource-links check-deposits-orcids])

(defn check-publisher [publisher]
  (reduce (fn [rslt chk-fn] 
            (let [check-result (chk-fn (:id publisher))]
              {:flags (merge (:flags rslt) (:flags check-result))
               :coverage (merge (:coverage rslt) (:coverage check-result))}))
          {} 
          checkles))

(defn check-publishers
  "Calculate and insert publisher/member quality metrics into a collection."
  [collection]
  (m/with-mongo (conf/get-service :mongo)
    (doseq [publisher (m/fetch collection)]
      (m/update! collection
                 publisher
                 (merge publisher (check-publisher publisher))))))
               

                           
                  
    
    
    
    
