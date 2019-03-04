(ns cayenne.api-fixture
  (:require [clj-http.client :as http]
            [clj-time.core :as clj-time]))

(defonce api-root "http://localhost:3000")

; Narnia – where it's always winter but never Christmas.
(def frozen-time (clj-time/date-time 2018 12 1))

(defn api-with
  [with-f]
  (fn [f]
    (try
      ; Run all tests at a known, given point in time.
      (clj-time/do-at
       frozen-time
       (user/start)
       (with-f)
       (f))
      (finally
        (user/stop)))))

(defn api-get [route & {:keys [sorter] :or {sorter :DOI}}]
  (let [message (-> (http/get (str api-root route) {:as :json})
                    :body
                    :message)]
    (cond-> message
      (:last-status-check-time message) (dissoc :last-status-check-time)
      (:indexed message) (dissoc :indexed)
      (:items message) (-> (update :items (partial map #(dissoc % :indexed :last-status-check-time)))
                           (update :items (partial sort-by sorter)))
      (:descendants message) (update :descendants sort))))

(defn no-scores
  "Update an API result, removing all scores."
  [m]
  (cond-> m
      (:score m) (dissoc :score)
      (:items m) (-> (update :items (partial map #(dissoc % :score))))))

(def api-with-works
  "Function to build an index with test data, with a callback function to execute in that context."
  (api-with user/index-feed))

(def feed-ready-api
  "Provide an empty API setup that's ready to index data."
  (api-with user/setup-feed))

