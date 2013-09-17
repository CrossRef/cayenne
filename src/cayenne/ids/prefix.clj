(ns cayenne.ids.prefix
  (:require [cayenne.ids :as ids]))

(defn extract-prefix [s]
  (when s
    (re-find #"10\.\d+" s)))

(defn to-prefix-uri [s]
  (when s
    (ids/get-id-uri :owner-prefix (extract-prefix s))))

