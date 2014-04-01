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

(defn ->response-doc [deposit-doc & {:keys [length] :or {:length false}}]
  (let [clean-doc (-> deposit-doc
                      (dissoc :data-id)
                      (dissoc :_id))]
    (if length
      (m/with-mongo (conf/get-service :mongo)
        (let [deposit-file (m/fetch-one-file 
                            :deposits 
                            :where {:_id (:data-id deposit-doc)})]
          (assoc clean-doc :length (:length deposit-file))))
      clean-doc)))

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

(defn fetch-data [owner batch-id]
  (m/with-mongo (conf/get-service :mongo)
    (when-let [deposit (m/fetch-one :deposits :where {:batch-id batch-id :owner owner})]
      (m/stream-from :deposits 
                     (m/fetch-one-file :deposits :where {:_id (:data-id deposit)})))))

(defn fetch-one [owner batch-id]
  (m/with-mongo (conf/get-service :mongo)
    (when-let [deposit (m/fetch-one :deposits 
                                    :where {:batch-id batch-id
                                            :owner owner})]
      (r/api-response
       :deposit
       :content (->response-doc deposit :length true)))))

(defn fetch [owner]
  (m/with-mongo (conf/get-service :mongo)
    (let [deposits (m/fetch :deposits :where {:owner owner})]
      (-> (r/api-response :deposit-list)
          (r/with-result-items 
            (count deposits) 
            (map #(->response-doc % :length true) deposits))))))

(defn fetch-for-doi [owner doi]
  (m/with-mongo (conf/get-service :mongo)
    ()))
    

(defn fetch-dois [owner]
  ())

