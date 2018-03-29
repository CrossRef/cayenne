(ns cayenne.api-fixture
  (:require [clj-http.client :as http]))

(defonce api-root "http://localhost:3000")

(defn api-with [with-f]
  (fn [f]
    (try 
      (user/start) 
      (with-f)
      (f)
      (finally
        (user/stop)))))

(defn api-get [route]
  (let [message (-> (http/get (str api-root route) {:as :json})
                    :body
                    :message)]
    (cond-> message
      (:last-status-check-time message) (dissoc :last-status-check-time)
      (:indexed message) (dissoc :indexed)
      (:items message) (-> (update :items (partial map #(dissoc % :indexed :last-status-check-time)))
                           (update :items (partial sort-by :DOI))))))

(def api-with-works
  (api-with user/process-feed))

(def feed-ready-api
  (api-with user/setup-for-feeds))
