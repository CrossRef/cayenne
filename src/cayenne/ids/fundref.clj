(ns cayenne.ids.fundref
  (:require [cayenne.ids :as ids]
            [cayenne.ids.doi :as doi]))

(def funder-prefix "10.13039")

(defn id-to-doi
  [id]
  (str funder-prefix "/" id))

(defn id-to-doi-uri
  [id]
  (doi/to-long-doi-uri (id-to-doi id)))

(defn doi-to-id
  [doi]
  ())

(defn doi-uri-to-id
  [doi-uri]
  ())
