(ns cayenne.conf
  (:import [org.neo4j.server WrappingNeoServerBootstrapper]
           [org.neo4j.kernel EmbeddedGraphDatabase]
           [org.apache.solr.client.solrj.impl HttpSolrServer])
  (:use [clojure.core.incubator :only [dissoc-in]])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [riemann.client :as rie]
            [somnium.congomongo :as m]
            [clj-http.conn-mgr :as conn]))

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

(defn get-resource [name]
  (.getFile (clojure.java.io/resource (get-param [:res name]))))

(defn file-writer [file-name]
  (let [wrtr (io/writer file-name)]
    (add-watch (agent nil) :file-writer
               (fn [key agent old new]
                 (.write wrtr new)
                 (.flush wrtr)))))

(defn write-to [file-writer msg]
  (send file-writer (constantly msg)))

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
    (set-service! :conn-mgr (conn/make-reusable-conn-manager {:timeout 120 :threads 3}))
    ;(set-service! :neo4j-db (EmbeddedGraphDatabase. (get-param [:service :neo4j :dir])))
    ;(set-service! :neo4j-server (WrappingNeoServerBootstrapper. (get-service :neo4j-db)))
    (set-service! :mongo (m/make-connection (get-param [:service :mongo :db])
                                            :host (get-param [:service :mongo :host])))
    (set-service! :solr (HttpSolrServer. (get-param [:service :solr :url])))
    (set-service! :riemann (rie/tcp-client :host (get-param [:service :riemann :host])))))

(defn set-core! [name]
  (alter-var-root #'*core-name* (constantly name)))

(with-core :default
  (set-param! [:dir :home] (System/getProperty "user.dir"))
  (set-param! [:dir :data] (str (get-param [:dir :home]) "/data"))
  (set-param! [:dir :test-data] (str (get-param [:dir :home]) "/test-data"))
  (set-param! [:dir :tmp] (str (get-param [:dir :home]) "/tmp"))

  (set-param! [:service :neo4j :dir] (str (get-param [:dir :data]) "/neo4j"))
  (set-param! [:service :mongo :db] "crossref")
  (set-param! [:service :mongo :host] "5.9.51.150")
  (set-param! [:service :riemann :host] "127.0.0.1")
  (set-param! [:service :solr :url] "http://78.46.87.34:8080/solr-web")

  (set-param! [:oai :crossref-journals :dir] (str (get-param [:dir :data]) "/oai/crossref-journals"))
  (set-param! [:oai :crossref-journals :url] "http://oai.crossref.org/OAIHandler")
  (set-param! [:oai :crossref-journals :type] "cr_unixml")
  (set-param! [:oai :crossref-journals :set-spec] "J")

  (set-param! [:oai :crossref-books :dir] (str (get-param [:dir :data]) "/oai/crossref-books"))
  (set-param! [:oai :crossref-books :url] "http://oai.crossref.org/OAIHandler")
  (set-param! [:oai :crossref-books :type] "cr_unixml")
  (set-param! [:oai :crossref-books :set-spec] "B")

  (set-param! [:oai :crossref-serials :dir] (str (get-param [:dir :data]) "/oai/crossref-serials"))
  (set-param! [:oai :crossref-serials :url] "http://oai.crossref.org/OAIHandler")
  (set-param! [:oai :crossref-serials :type] "cr_unixml")
  (set-param! [:oai :crossref-serials :set-spec] "S")

  (set-param! [:oai :datacite :dir] (str (get-param [:dir :data]) "/oai/datacite"))
  (set-param! [:oai :datacite :url] "http://oai.datacite.org/oai")
  (set-param! [:oai :datacite :type] "datacite")

  (set-param! [:id :issn :path] "http://id.crossref.org/issn/")
  (set-param! [:id :isbn :path] "http://id.crossref.org/isbn/")
  (set-param! [:id :orcid :path] "http://orcid.org/")
  (set-param! [:id :long-doi :path] "http://dx.doi.org/")
  (set-param! [:id :short-doi :path] "http://doi.org/")
  
  (set-param! [:id-generic :path] "http://id.crossref.org/")
  (set-param! [:id-generic :data-path] "http://data.crossref.org/")

  (set-param! [:res :tld-list] "tlds.txt")
  (set-param! [:res :funders] "funders.csv")

  (set-param! [:upstream :openurl-url] "http://www.crossref.org/openurl/?noredirect=true&pid=kward@crossref.org&format=unixref&id=doi:")
  (set-param! [:upstream :prefix-info-url] "http://www.crossref.org/getPrefixPublisher/?prefix="))

(set-core! :default)

