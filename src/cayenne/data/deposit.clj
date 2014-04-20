(ns cayenne.data.deposit
  (:import [java.util Date])
  (:require [metrics.gauges :refer [defgauge]]
            [somnium.congomongo :as m]
            [cayenne.conf :as conf]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.query :as q]
            [cayenne.api.v1.filter :as f]
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
                      (dissoc :passwd)
                      (dissoc :_id))]
    (if length
      (m/with-mongo (conf/get-service :mongo)
        (let [deposit-file (m/fetch-one-file 
                            :deposits 
                            :where {:_id (:data-id deposit-doc)})]
          (assoc clean-doc :length (:length deposit-file))))
      clean-doc)))

(defn create! [deposit-data type batch-id dois owner passwd test pingback-url]
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
                              :passwd passwd
                              :test test
                              :pingback-url pingback-url
                              :status :submitted
                              :handoff {:status :incomplete
                                        :timestamp 0
                                        :try-count 0
                                        :delay-millis 0}
                              :submitted-at (Date.)})]
      (hist/update! deposit-size (:length new-file))
      (id->s new-doc))))

(defn begin-handoff! 
  "Call to begin hand-off or a hand-off try."
  [batch-id & {:keys [delay-fn] :or {delay-fn (fn [_ x] x)}}]
  (m/with-mongo (conf/get-service :mongo)
    (let [deposit-data (m/fetch-one :deposits :where {:batch-id batch-id})
          curr-try-count (get-in deposit-data [:handoff :try-count])
          curr-delay-millis (get-in deposit-data [:handoff :delay-millis])
          next-delay-millis (delay-fn curr-delay-millis (inc curr-try-count))]
      (m/update! :deposits
                 deposit-data
                 (-> deposit-data
                     (assoc-in [:handoff :timestamp] (System/currentTimeMillis))
                     (assoc-in [:handoff :try-count] (inc curr-try-count))
                     (assoc-in [:handoff :delay-millis] next-delay-millis)))
      next-delay-millis)))

(defn end-handoff!
  "Call to indicate successful hand-off process."
  [batch-id]
  (m/with-mongo (conf/get-service :mongo)
    (m/update! :deposits
               {:batch-id batch-id}
               {"$set" {"handoff.status" :completed}})))

(defn failed! [batch-id]
  (m/with-mongo (conf/get-service :mongo)
    (m/update! :deposits {:batch-id batch-id} {"$set" {:status :failed}})))

(defn fetch-data [query-context]
  (m/with-mongo (conf/get-service :mongo)
    (let [where-clause (-> query-context
                           (q/->mongo-query
                            :filters f/deposit-filters
                            :id-field :batch-id)
                           second)]
      (when-let [deposit (m/fetch-one :deposits :where where-clause)]
        (m/stream-from :deposits 
                       (m/fetch-one-file :deposits :where {:_id (:data-id deposit)}))))))

(defn fetch-one [query-context]
  (m/with-mongo (conf/get-service :mongo)
    (let [where-clause (-> query-context
                           (q/->mongo-query
                            :filters f/deposit-filters
                            :id-field :batch-id)
                           second)]
      (when-let [deposit (m/fetch-one :deposits :where where-clause)]
        (r/api-response
         :deposit
         :content (->response-doc deposit :length true))))))

(defn fetch [query-context]
  (m/with-mongo (conf/get-service :mongo)
    (let [query (q/->mongo-query 
                 query-context 
                 :filters f/deposit-filters)
          deposits (if (and (:rows query-context) (zero? (:rows query-context)))
                     []
                     (apply m/fetch :deposits query))
          deposits-count (apply m/fetch-count :deposits query)]
      (-> (r/api-response :deposit-list)
          (r/with-query-context-info query-context)
          (r/with-result-items 
            deposits-count
            (map #(->response-doc % :length true) deposits))))))

