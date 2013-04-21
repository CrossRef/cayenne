(ns cayenne.conf
  (:require [clojure.data.json :as json]))

(def parameters (atom {}))

(defn get-param [path & default]
  (or (get-in @parameters path) default))

(defn set-param! [path value]
  (swap! parameters assoc-in path value))

(set-param! [:db :neo4j :dir] "/home/cayenne/data/neo4j")

(set-param! [:oai :dir] "/home/cayenne/data/oai")
(set-param! [:oai :crossref :uri] "http://oai.crossref.org")
(set-param! [:oai :datacite :uri] "http://oai.datacite.org")

(set-param! [:id :issn :path] "http://id.crossref.org/issn/")
(set-param! [:id :isbn :path] "http://id.crossref.org/isbn/")
(set-param! [:id :orcid :path] "http://orcid.org/")
(set-param! [:id :long-doi :path] "http://dx.doi.org/")
(set-param! [:id :short-doi :path] "http://doi.org/")

(set-param! [:id :path] "http://id.crossref.org/")
(set-param! [:id :data-path] "http://data.crossref.org/")

(defn get-id-uri [id-type id-value]
  (if-let [prefix (get-param [:id (symbol id-type) :path])]
    (str prefix id-value)
    (str (get-param [:id :path]) (name id-type) "/" id-value)))

(defn get-data-uri [id-type id-value]
  (if-let [prefix (get-param [:id (symbol id-type) :data-path])]
    (str prefix id-value)
    (str (get-param [:id :data-path]) (name id-type) "/" id-value)))

