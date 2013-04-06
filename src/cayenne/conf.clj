(ns cayenne.conf
  (:require [clojure.data.json :as json]))

(def parameters (atom {}))

(defn get-param [path & default]
  (or (get-in @parameters path) default))

(defn set-param! [path value]
  (swap! parameters assoc-in path value))

(set-param! [:dir :oai] "/home/cayenne/data/oai")
(set-param! [:dir :neo4j] "/home/cayenne/data/neo4j")

(set-param! [:oai :crossref :uri] "http://oai.crossref.org")
(set-param! [:oai :datacite :uri] "http://oai.datacite.org")

