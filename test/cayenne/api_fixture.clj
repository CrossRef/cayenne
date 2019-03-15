(ns cayenne.api-fixture
  (:require [clj-http.client :as http]
            [clj-time.core :as clj-time]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]))

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

(defn clean-api-response
  [message {:keys [sorter] :or {sorter :DOI}}]
  (cond-> message
    (:last-status-check-time message) (dissoc :last-status-check-time)
    (:indexed message) (dissoc :indexed)
    (:items message) (-> (update :items (partial map #(dissoc % :indexed :last-status-check-time)))
                         (update :items (partial sort-by sorter)))
    (:descendants message) (update :descendants sort)))

(defn api-get-network
  "Make an API request via the HTTP stack."
  [route & options]
  (-> (http/get (str api-root route) {:as :json})
                    :body
                    :message
      (clean-api-response options)))

(defn api-get
  "Make an API request via directly via the Ring routes."
  [route & options]
  (let [api-handler (cayenne.api.route/create-handler)]
        (-> (mock/request :get route)
            api-handler
            :body
            (json/read-str :key-fn keyword)
            :message
            (clean-api-response options))))

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

(def empty-api
  "An empty set up with indexes but no data."
  (api-with (constantly nil)))