(ns cayenne.ids.prefix
  (:require [cayenne.ids :as ids]))

(defn is-prefix? [s]
  (when s (not (nil? (re-find #"\A10\.\d+\Z" s)))))

(defn extract-prefix [s]
  (when s
    (re-find #"10\.\d+" s)))

(defn normalize-prefix [s]
  (extract-prefix s))

(defn to-prefix-uri [s]
  (when s
    (ids/get-id-uri :owner-prefix (extract-prefix s))))

