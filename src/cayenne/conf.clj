(ns cayenne.conf
  (:import [org.neo4j.kernel EmbeddedGraphDatabase])
  (:use [clojure.core.incubator :only [dissoc-in]])
  (:require [clojure.data.json :as json]
            [riemann.client :as rie]))

(def cores (atom {}))
(def ^:dynamic *core-name*)
(def ^:dynamic *result-name*)

(defn get-param [path & default]
  (or (get-in @cores (concat [*core-name* :parameters] path) default)))

(defn set-param! [path value]
  (swap! cores assoc-in (concat [*core-name* :parameters] path) value))

(defn get-service [key]
  (get-in @cores [*core-name* :services key]))

(defn set-service! [key obj]
  (swap! cores assoc-in [*core-name* :services key] obj))

(defn set-result! [key val]
  (swap! cores assoc-in [*core-name* :results *result-name* key] val))

(defn update-result! [key f]
  (swap! cores update-in [*core-name* :results *result-name* key] f))

(defn get-result [key]
  (get-in @cores [*core-name* :results *result-name* key]))

(defn drop-result-set! []
  (swap! cores dissoc-in [*core-name* :results *result-name*]))

(defmacro with-core 
  "Run operations on a particular core."
  [name & body]
  `(binding [*core-name* ~name]
     ~@body))

(defmacro with-result-set
  "Run tasks within the context of a result, which they can update and
   write to."
  [name & body]
  `(binding [*result-name* ~name]
     ~@body))

(defn create-core! 
  "Create a new named core, initializes various services."
  [name & opts]
  (with-core name
    (set-service! :neo4j-db (EmbeddedGraphDatabase. (get-param [:services :neo4j :dir])))
    ;(set-service! :neo4j-server (WrappingNeoServerBootstrapper. (get-service :neo4j-db)))
    (set-service! :riemann (rie/tcp-client :host (get-param [:services :riemann :host])))))

(defn set-core! [name]
  (alter-var-root #'*core-name* (constantly name)))

(defn get-id-uri [id-type id-value]
  (if-let [prefix (get-param [:id (keyword id-type) :path])]
    (str prefix id-value)
    (str (get-param [:id :path]) (name id-type) "/" id-value)))

(defn get-data-uri [id-type id-value]
  (if-let [prefix (get-param [:id (keyword id-type) :data-path])]
    (str prefix id-value)
    (str (get-param [:id :data-path]) (name id-type) "/" id-value)))

(with-core :default
  (set-param! [:service :neo4j :dir] "/home/cayenne/data/neo4j")
  (set-param! [:service :riemann :host] "127.0.0.1")
  
  (set-param! [:oai :dir] "/home/cayenne/data/oai")
  (set-param! [:oai :crossref :uri] "http://oai.crossref.org")
  (set-param! [:oai :datacite :uri] "http://oai.datacite.org")

  (set-param! [:id :issn :path] "http://id.crossref.org/issn/")
  (set-param! [:id :isbn :path] "http://id.crossref.org/isbn/")
  (set-param! [:id :orcid :path] "http://orcid.org/")
  (set-param! [:id :long-doi :path] "http://dx.doi.org/")
  (set-param! [:id :short-doi :path] "http://doi.org/")
  
  (set-param! [:id :path] "http://id.crossref.org/")
  (set-param! [:id :data-path] "http://data.crossref.org/"))

(set-core! :default)

