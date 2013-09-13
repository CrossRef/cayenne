(ns cayenne.data.publisher
  (:require [cayenne.conf :as conf]))

(def filters
  {"from-deposit-date"
   "until-deposit-date"
   "from-pub-date"
   "until-pub-date"
   "has-full-text-link"
   "has-license"
   "funder"})

(defn fetch [query-context]
  ())

(defn fetch-one [query-context]
  ())

(defn fetch-works [query-context]
  ())
