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

