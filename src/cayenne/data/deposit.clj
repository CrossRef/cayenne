(ns cayenne.data.deposit
  (:import [java.util Date])
  (:require [metrics.gauges :refer [defgauge]]
            [somnium.congomongo :as m]
            [cayenne.conf :as conf]
            [cayenne.api.v1.response :as r]
            [metrics.meters :refer [defmeter] :as meter]
            [metrics.histograms :refer [defhistogram] :as hist]))

(defhistogram [cayenne data deposit-size])

(defmeter [cayenne data deposits-received] "deposits-received")

(defgauge [cayenne data deposit-count]
  (m/with-mongo (conf/get-service :mongo)
    (m/fetch-count :deposits)))

(defn ensure-deposit-indexes! [collection-name]
  (m/add-index! collection-name [:batch-id])
  (m/add-index! collection-name [:owner :batch-id])
  (m/add-index! collection-name [:owner :submitted-at])
  (m/add-index! collection-name [:owner :dois])
  (m/add-index! collection-name [:owner :status]))

(defn id->s [doc]
  (-> doc (:_id) (.toString)))

(defn create! [deposit-data type batch-id dois owner]
  (meter/mark! deposits-received)
  (m/with-mongo (conf/get-service :mongo)
    (ensure-deposit-indexes! :deposits)
    (let [new-file (m/insert-file! :deposits deposit-data)
          new-doc (m/insert! :deposits
                             {:content-type type
                              :data-id (:_id new-file)
                              :batch-id batch-id
                              :dois dois
                              :owner owner
                              :status :submitted
                              :submitted-at (Date.)})]
      (hist/update! deposit-size (:length new-file))
      (id->s new-doc))))

(defn fetch-data [batch-id]
  (m/with-mongo (conf/get-service :mongo)
    (when-let [deposit (m/fetch-one :deposits :where {:batch-id batch-id})]
      (m/stream-from :deposits 
                     (m/fetch-one-file :deposits :where {:_id (:data-id deposit)})))))

(defn fetch [batch-id]
  (m/with-mongo (conf/get-service :mongo)
    (when-let [deposit (m/fetch-one :deposits :where {:batch-id batch-id})]
      (let [deposit-file (m/fetch-one-file :deposits :where {:_id (:data-id deposit)})
            deposit-info (-> deposit
                             (dissoc :data-id)
                             (dissoc :_id)
                             (assoc :length (:length deposit-file)))]
        (r/api-response :deposit :content deposit-info)))))

(defn fetch-for-doi [owner doi]
  (m/with-mongo (conf/get-param :mongo)
    ()))
    

(defn fetch-dois []
  ())

