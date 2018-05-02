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
(defn recursive-dissoc
  "like dissoc, but works recursively. Only works on reference-free maps (contains no atoms)."
  [a-map pred]
  (if (not (empty? a-map))
    (let [k (first (first a-map))
          v (second (first a-map))]
      (if (pred k)
        (recursive-dissoc (dissoc a-map k)
                          pred)
        (conj
         {k (cond (map? v)
                  (recursive-dissoc v pred)
                  true v)}
         (recursive-dissoc (dissoc a-map k)
                           pred))))
    {}))
(defn api-get [route]
  (let [message (-> (http/get (str api-root route) {:as :json})
                    :body
                    :message)]
    (cond-> message
      (:last-status-check-time message) (dissoc :last-status-check-time)
      (:indexed message) (dissoc :indexed)
      (:items message) (-> (update :items (partial map #(dissoc % :indexed :last-status-check-time)))
                           (update :items (partial sort-by :DOI))
                           (update :items #(conj (for [item %] (recursive-dissoc item (fn [x](= x :last-status-check-time))))))))))

(def api-with-works
  (api-with user/process-feed))

(def feed-ready-api
  (api-with user/setup-for-feeds))
