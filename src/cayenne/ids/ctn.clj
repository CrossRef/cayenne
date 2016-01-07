(ns cayenne.ids.ctn)
(defn normalize-ctn
  "Normalize a CTN for display."
  [ctn]
  (when ctn
    (.toLowerCase ctn)))


(defn ctn-proxy
  "Normalize a CTN to a proxy for search."
  [ctn]
  (->
    ctn
    (.toLowerCase)
    (clojure.string/replace #"[^a-z0-9]" "")))