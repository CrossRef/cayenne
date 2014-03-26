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

(defn id->s [doc]
  (-> doc (:_id) (.toString)))

(defn create! [deposit-data type batch-id dois]
  (meter/mark! deposits-received)
  (m/with-mongo (conf/get-service :mongo)
    (let [new-file (m/insert-file! :deposits deposit-data)
          new-doc (m/insert! :deposits
                             {:content-type type
                              :data-id (:_id new-file)
                              :batch-id batch-id
                              :dois dois
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

(defn fetch-for-doi [doi]
  ())

(defn fetch-dois []
  ())

