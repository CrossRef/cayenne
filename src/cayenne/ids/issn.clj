(ns cayenne.ids.issn
  (:require [clojure.string :as string])
  (:require [cayenne.ids :as ids]))

(def digit-set #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \X \x})

(defn is-issn?
  "Return true if s is a valid, normalized ISSN without URI prefix.
   Does not check checksum digit."
  [s]
  (not (nil? (re-find #"\A[0-9]{4}-[0-9]{3}[0-9X]\Z" s))))

(defn extract-issn 
  "Find anything in s that looks like it may be an ISSN and return it
   verbatim without URI prefix."
  [s]
  (first (re-find #"(\d[^\dxX]*){7}[0-9Xx]" s)))

(defn normalize-issn 
  "Find anything in s that looks like it may be an ISSN and return it
   in a normalized form without a URI prefix."
  [s]
  (when s
    (let [digits (filter digit-set (extract-issn s))
          parts (map #(apply str %) (partition 4 digits))]
      (if (string/blank? (apply str digits))
        nil
        (.toUpperCase (string/join "-" parts))))))

(defn to-issn-uri 
  "Find anything in s that looks like it may be an ISSN and return it
  in a normalized URI form."
  [s]
  (when s
    (ids/get-id-uri :issn (normalize-issn s))))

