(ns cayenne.conf
  (:import [org.apache.solr.client.solrj.impl HttpSolrServer]
           [java.net URI]
           [java.util UUID])
  (:use [clojure.core.incubator :only [dissoc-in]])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.trace :as trace]
            [riemann.client :as rie]
            [somnium.congomongo :as m]
            [clj-http.conn-mgr :as conn]
            [clojurewerkz.neocons.rest :as nr]
            [org.httpkit.server :as hs]))

(def cores (atom {}))
(def ^:dynamic *core-name*)

(defn get-param [path & default]
  (or (get-in @cores (concat [*core-name* :parameters] path) default)))

(defn set-param! [path value]
  (swap! cores assoc-in (concat [*core-name* :parameters] path) value))

(defn get-service [key]
  (get-in @cores [*core-name* :services key]))

(defn set-service! [key obj]
  (swap! cores assoc-in [*core-name* :services key] obj))

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

(defn create-core! [name]
  (swap! cores assoc name {}))

(defn create-core-from! [name copy-from-name]
  (let [params (get-in @cores [copy-from-name :parameters])]
    (swap! cores assoc-in [name :parameters] params)))

(defn start-core!
  "Create a new named core, initializes various services."
  [name & opts]
  (with-core name
    (set-service! :api (hs/run-server (get-param [:service :api :var])
                                    {:join? false
                                     :port (get-param [:service :api :port])}))
    (set-service! :conn-mgr (conn/make-reusable-conn-manager {:timeout 120 :threads 3}))
    (set-service! :mongo (m/make-connection (get-param [:service :mongo :db])
                                            :host (get-param [:service :mongo :host])))
    (set-service! :solr (HttpSolrServer. (get-param [:service :solr :url])))
    (set-service! :solr-update-list
                  (map #(HttpSolrServer. (str (:url %) "/" (:core %)))
                       (get-param [:service :solr :update-list])))
    ;(set-service! :riemann (rie/tcp-client :host (get-param [:service :riemann :host])))
    ;(set-service! :neo4j (nr/connect! (get-param [:service :neo4j :url])))
    (set-param! [:status] :running)))

(defn stop-core! [name]
  (with-core name
    (.stop (get-service :api))
    (set-param! [:status] :stopped)))

(defn set-core! [name]
  (alter-var-root #'*core-name* (constantly name)))

(defn test-input-file [name]
  (io/file (str (get-param [:dir :test-data]) "/" name ".xml")))

(defn test-accepted-file [name test-name]
  (io/file (str (get-param [:dir :test-data]) "/" name "-" test-name ".accepted")))

(defn test-output-file [name test-name]
  (io/file (str (get-param [:dir :test-data]) "/" name "-" test-name ".out")))

(defn remote-file [url]
  (let [content (slurp (URI. url))
        path (str (get-param [:dir :tmp]) "/remote-" (UUID/randomUUID) ".tmp")]
    (spit (io/file path) content)
    (io/file path)))

;; todo move default service config to the files that maintain maintan the service.

(with-core :default
  (set-param! [:env] :none)
  (set-param! [:status] :stopped)
  (set-param! [:dir :home] (System/getProperty "user.dir"))
  (set-param! [:dir :data] (str (get-param [:dir :home]) "/data"))
  (set-param! [:dir :test-data] (str (get-param [:dir :home]) "/test-data"))
  (set-param! [:dir :tmp] (str (get-param [:dir :home]) "/tmp"))

  (set-param! [:service :solr :update-list]
              [{:url "http://localhost:8983/solr" :core "crmds1"}])

  (set-param! [:service :mongo :db] "crossref")
  (set-param! [:service :mongo :host] "5.9.51.150")
  (set-param! [:service :riemann :host] "127.0.0.1")
  (set-param! [:service :solr :url] "http://localhost:8983/solr")
  (set-param! [:service :solr :query-core] "crmds1")                
  (set-param! [:service :solr :insert-list-max-size] 1000)
  (set-param! [:service :neo4j :url] "http://localhost:7474/db/data")
  (set-param! [:service :api :port] 3000)
  (set-param! [:service :queue :host] "5.9.51.150")

  (set-param! [:oai :datacite :dir] (str (get-param [:dir :data]) "/oai/datacite"))
  (set-param! [:oai :datacite :url] "http://oai.datacite.org/oai")
  (set-param! [:oai :datacite :type] "datacite")

  (set-param! [:id :issn :path] "http://id.crossref.org/issn/")
  (set-param! [:id :isbn :path] "http://id.crossref.org/isbn/")
  (set-param! [:id :orcid :path] "http://orcid.org/")
  (set-param! [:id :owner-prefix :path] "http://id.crossref.org/prefix/")
  (set-param! [:id :long-doi :path] "http://dx.doi.org/")
  (set-param! [:id :short-doi :path] "http://doi.org/")
  (set-param! [:id :supplementary :path] "http://id.crossref.org/supp/")
  
  (set-param! [:id-generic :path] "http://id.crossref.org/")
  (set-param! [:id-generic :data-path] "http://data.crossref.org/")

  (set-param! [:res :tld-list] "tlds.txt")
  (set-param! [:res :funders] "funders.csv")

  (set-param! [:upstream :crmds-dois] "http://search.crossref.org/dois?q=")
  (set-param! [:upstream :fundref-dois-live] "http://search.crossref.org/funders/dois?rows=10000000000")
  (set-param! [:upstream :fundref-dois-dev] "http://search-dev.labs.crossref.org/funders/dois?rows=10000000000")
  (set-param! [:upstream :fundref-registry] "http://dx.doi.org/10.13039/fundref_registry")
  (set-param! [:upstream :openurl-url] "http://www.crossref.org/openurl/?noredirect=true&pid=kward@crossref.org&format=unixref&id=doi:")
  (set-param! [:upstream :prefix-info-url] "http://www.crossref.org/getPrefixPublisher/?prefix="))

(set-core! :default)

