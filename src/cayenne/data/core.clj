(ns cayenne.data.core
  (:require [clojure.core.incubator :refer [dissoc-in]]
            [cayenne.conf :as conf]))

(defn exists? [name]
  (some #{(keyword name)} (keys @conf/cores)))

(defn fetch [name]
  (get @conf/cores (keyword name)))

(defn fetch-all []
  (keys @conf/cores))

