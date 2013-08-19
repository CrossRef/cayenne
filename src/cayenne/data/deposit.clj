(ns cayenne.data.deposit
  (:import [java.util Date])
  (:require [metrics.gauges :refer [defgauge]]
            [somnium.congomongo :as m]
            [cayenne.api.ingest :as ingest]
            [cayenne.conf :as conf]
            [metrics.meters :refer [defmeter] :as meter]
            [metrics.histograms :refer [defhistogram] :as hist]))

(defhistogram [cayenne data deposit-size])
(defmeter [cayenne data deposits-received] "deposits-received")

(defn id->s [doc]
  (-> doc (:_id) (.toString)))

(defn create! [type deposit-data]
  (meter/mark! deposits-received)
  (hist/update! deposit-size (count deposit-data))
  (m/with-mongo (conf/get-service :mongo)
    (let [new-doc (m/insert! :deposits
                             {:processed false
                              :type type
                              :data deposit-data
                              :created-at (Date.)})]
      (id->s new-doc))))

(defn fetch-data [id]
  (m/with-mongo (conf/get-service :mongo)
    (when-let [deposit (m/fetch-by-id :deposits (m/object-id id))]
      (:data deposit))))

(defn fetch [id]
  (m/with-mongo (conf/get-service :mongo)
    (when-let [deposit (m/fetch-by-id :deposits (m/object-id id))]
      (dissoc deposit :data))))
      
(defgauge [cayenne data deposit-count]
  (m/with-mongo (conf/get-service :mongo)
    (m/fetch-count :deposits)))
