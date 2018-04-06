(ns cayenne.conf
  (:import [java.net URI]
           [java.util UUID]
           [java.util.concurrent Executors])
  (:use [clojure.core.incubator :only [dissoc-in]])
  (:require [cayenne.version]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.trace :as trace]
            [qbits.spandex :as elastic]
            [clj-http.conn-mgr :as conn]
            [clojure.tools.nrepl.server :as nrepl]
            [robert.bruce :as rb]))

(def cores (atom {}))
(def ^:dynamic *core-name*)

(def startup-tasks (atom {}))

(def shutdown-tasks (atom {}))

(defn get-param [path & default]
  (get-in @cores (concat [*core-name* :parameters] path) default))

(defn set-param! [path value]
  (swap! cores assoc-in (concat [*core-name* :parameters] path) value))

(defn get-service [key]
  (get-in @cores [*core-name* :services key]))

(defn set-service! [key obj]
  (swap! cores assoc-in [*core-name* :services key] obj))

(defn add-startup-task [key task]
  (swap! startup-tasks assoc key task))

(defn get-resource [name]
  (-> (get-param [:res name]) io/resource))

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
  [name & profiles]
  (doseq [p-name (concat [:base] profiles)]
    (when-let [task (get @startup-tasks p-name)]
      (print "Starting" p-name "... ")
      (task profiles)
      (println "done.")))
  (with-core name
    (set-param! [:status] :running)))

(defn stop-core! [name]
  (with-core name
    (let [stop-fn (get-service :api)]
      (stop-fn :timeout 100)) ; stop http kit allowing 100ms to close open connections
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
  (rb/try-try-again
   {:tries 10
    :error-hook #(prn "Failed to retrieve url " url " - " %)}
   #(let [content (slurp (URI. url))
          path (str (get-param [:dir :tmp]) "/remote-" (UUID/randomUUID) ".tmp")]
      (spit (io/file path) content)
      (io/file path))))

;; todo move default service config to the files that maintain maintan the service.

(with-core :default
  (set-param! [:env] :none)
  (set-param! [:status] :stopped)
  (set-param! [:version] cayenne.version/version)
  (set-param! [:dir :home] (System/getProperty "user.dir"))
  (set-param! [:dir :data] (str (get-param [:dir :home]) "/data"))
  (set-param! [:dir :test-data] (str (get-param [:dir :home]) "/test-data"))
  (set-param! [:dir :tmp] (str (get-param [:dir :home]) "/tmp"))

  (set-param! [:service :elastic :urls] ["http://localhost:9200"])
  (set-param! [:service :api :port] 3000)
  (set-param! [:service :queue :host] "5.9.51.150")
  (set-param! [:service :logstash :host] "5.9.51.2")
  (set-param! [:service :logstash :port] 4444)
  (set-param! [:service :logstash :name] "cayenne-api")
  (set-param! [:service :nrepl :port] 7888)

  (set-param! [:deposit :email] "crlabs@fastmail.fm")

  (set-param! [:id :issn :path] "http://id.crossref.org/issn/")
  (set-param! [:id :isbn :path] "http://id.crossref.org/isbn/")
  (set-param! [:id :orcid :path] "http://orcid.org/")
  (set-param! [:id :owner-prefix :path] "http://id.crossref.org/prefix/")
  (set-param! [:id :long-doi :path] "http://dx.doi.org/")
  (set-param! [:id :short-doi :path] "http://doi.org/")
  (set-param! [:id :supplementary :path] "http://id.crossref.org/supp/")
  (set-param! [:id :contributor :path] "http://id.crossref.org/contributor/")
  (set-param! [:id :member :path] "http://id.crossref.org/member/")

  (set-param! [:id-generic :path] "http://id.crossref.org/")
  (set-param! [:id-generic :data-path] "http://data.crossref.org/")

  (set-param! [:res :tld-list] "tlds.txt")
  (set-param! [:res :funders] "funders.csv")
  (set-param! [:res :locales] "locales.edn")
  (set-param! [:res :styles] "styles.edn")
  (set-param! [:res :tokens] "tokens.edn")
  (set-param! [:res :funder-update] "data/funder-update.date")

  (set-param! [:location :cr-titles-csv] "http://ftp.crossref.org/titlelist/titleFile.csv")
  (set-param! [:location :cr-funder-registry] "http://data.crossref.org/fundingdata/registry")
  (set-param! [:location :scopus-title-list] "https://www.elsevier.com/?a=91122&origin=sbrowse&zone=TitleList&category=TitleListLink")

  (set-param! [:test :doi] "10.5555/12345678")

  (set-param! [:upstream :pdf-service] "http://46.4.83.72:3000/pdf")
  (set-param! [:upstream :doi-url] "http://doi.crossref.org/search/doi?pid=cnproxy@crossref.org&format=unixsd&doi=")
  (set-param! [:upstream :doi-ra-url] "https://doi.crossref.org/doiRA/")
  (set-param! [:upstream :prefix-info-url] "http://doi.crossref.org/getPrefixPublisher/?prefix=")
  (set-param! [:upstream :crossref-auth] "https://doi.crossref.org/info")
  (set-param! [:upstream :crossref-test-auth] "http://test.crossref.org/info"))

(with-core :default
  (add-startup-task 
   :base
   (fn [profiles]
     (set-service! :executor
                   (Executors/newScheduledThreadPool 20))
     (set-service! :conn-mgr
                   (conn/make-reusable-conn-manager {:timeout 120 :threads 10}))
     (set-service! :elastic
                   (elastic/client {:hosts (get-param [:service :elastic :urls])})))))

(with-core :default
  (add-startup-task
   :nrepl
   (fn [profiles]
     (set-service!
      :nrepl
      (nrepl/start-server :port (get-param [:service :nrepl :port]))))))

(set-core! :default)

