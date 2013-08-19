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

(defgauge [cayenne data deposit-count]
  (m/with-mongo (conf/get-service :mongo)
    (m/fetch-count :deposits)))

(defn id->s [doc]
  (-> doc (:_id) (.toString)))

(defn create! [type deposit-data]
  (meter/mark! deposits-received)
  (m/with-mongo (conf/get-service :mongo)
    (let [new-file (m/insert-file! :deposits deposit-data)
          new-doc (m/insert! :deposits
                             {:processed false
                              :type type
                              :data-id (:_id new-file)
                              :created-at (Date.)})]
      (hist/update! deposit-size (:length new-file))
      (id->s new-doc))))

(defn fetch-data [id]
  (m/with-mongo (conf/get-service :mongo)
    (when-let [deposit (m/fetch-by-id :deposits (m/object-id id))]
      (m/stream-from :deposits 
                     (m/fetch-one-file :deposits :where {:_id (:data-id deposit)})))))

(defn fetch [id]
  (m/with-mongo (conf/get-service :mongo)
    (when-let [deposit (m/fetch-by-id :deposits (m/object-id id))]
      (let [deposit-file (m/fetch-one-file :deposits :where {:_id (:data-id deposit)})]
        (-> deposit
            (dissoc :data)
            (assoc :length (:length deposit-file)))))))

