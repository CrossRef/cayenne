(ns cayenne.ids.member
  (:require [cayenne.ids :as ids]))

(defn extract-member-id [s]
  (when s
    (re-find #"\d+" (str s))))

(defn to-member-id-uri [s]
  (when s
    (ids/get-id-uri :member (extract-member-id (str s)))))
