(ns cayenne.ids.orcid
  (:require [clojure.string :as string])
  (:require [cayenne.conf :as conf]))

(def digit-set #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \X \x})

(defn is-orcid?
  "Return true if s is a valid, normalized ORCID without URI prefix.
   Does not check checksum digit."
  [s]
  (not (nil? (re-find #"\A[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{3}[0-9X]\Z"))))

(defn extract-orcid
  [s]
  (first (re-find #"(\d[\s\-]*){15}[0-9Xx]" s)))

(defn normalize-orcid
  [s]
  (let [digits (filter digit-set (extract-orcid s))
        parts (map #(apply str %) (partition 4 digits))]
    (.toUpperCase (string/join "-" parts))))

(defn to-orcid-uri
  [s]
  (when s
    (conf/get-id-uri :orcid (normalize-orcid s))))

