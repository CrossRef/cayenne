(ns cayenne.api-fixture
  (:require [clj-http.client :as http]))

(defonce api-root "http://localhost:3000")

(defn api-with
  [with-f]
  (fn [f]
    (try
      (user/start)
      (with-f)
      (f)
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
  (api-with user/setup-feed))
