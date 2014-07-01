(ns cayenne.ids.fundref
  (:require [cayenne.ids :as ids]
            [cayenne.ids.doi :as doi]
            [clojure.string :as string]))

(def funder-prefix "10.13039")

(defn id-to-doi
  [id]
  (str funder-prefix "/" id))

(defn id-to-doi-uri
  [id]
  (doi/to-long-doi-uri (id-to-doi id)))

(defn doi-uri-to-id
  [doi]
  (last (string/split doi #"/")))

(defn normalize-to-doi-uri [s]
  (when s
    (-> s (string/split #"/") last id-to-doi-uri)))
