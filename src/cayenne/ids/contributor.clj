(ns cayenne.ids.contributor
  (:require [cayenne.ids :as ids]
            [clojure.string :as string])
  (:import [java.security MessageDigest]
           [java.text Normalizer Normalizer$Form]))

;; IDs for ambiguous contributors (these IDs embed a work ID.)

(defn contributor-slug [name]
  (-> (Normalizer/normalize name Normalizer$Form/NFD)
      (string/lower-case)
      (string/replace #"\." " ")
      (string/replace #"\s+" "-")
      (string/replace #"[^a-z-]" "")
      (string/replace #"\-+" "-")
      (string/replace #"\-+\Z" "")
      (string/replace #"\A-+" "")))

(defn work-slug [doi]
  (let [bytes (.digest (MessageDigest/getInstance "MD5")
                       (.getBytes doi "UTF-8"))
        hex-digest (.toString (BigInteger. 1 bytes) 16)
        shortened-digest (->> hex-digest
                              (drop 16)
                              (take 16)
                              (apply str))]
    (.toString (BigInteger. shortened-digest 16) 36)))

(defn contributor-id [name ordinal doi]
  (if (zero? ordinal)
    (str (contributor-slug name) "-" (work-slug doi))
    (str (contributor-slug name) "-" (+ ordinal 1) "-" (work-slug doi))))

(defn to-contributor-id-uri [name ordinal doi]
  (ids/get-id-uri :contributor (contributor-id name ordinal doi)))
