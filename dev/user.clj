(ns user
  (:require [cayenne.conf :refer [set-param! with-core cores start-core! stop-core!]]
            [cayenne.tasks :refer [load-funders]]
            [cayenne.rdf :as rdf]
            [cayenne.tasks :refer [load-members load-journals]]
            [cayenne.tasks.funder :refer [select-country-stmts]]
            [cayenne.tasks.coverage :refer [check-journals check-members]]
            [cayenne.tasks.solr :refer [start-insert-list-processing]]
            [cayenne.api.v1.feed :refer [start-feed-processing]]
            [clojure.java.io :refer [resource]]
            [clojure.java.shell :refer [sh]]
            [clj-http.client :as http]
            [somnium.congomongo :as m]
            [me.raynes.fs :refer [copy-dir delete-dir]]
            [nio2.io :refer [path]]
            [nio2.dir-seq :refer [dir-seq-glob]]))

(defn- solr-ready? []
  (try
    (= 200 (:status (http/get "http://localhost:8983/solr/crmds1/admin/ping")))
    (catch Exception e
      false)))

(defn solr-doc-count []
  (-> (http/get "http://localhost:8983/solr/admin/cores?action=STATUS&wt=json" {:as :json})
      :body
      :status
      :crmds1
      :index
      :numDocs))

(defn- mongo-ready? []
  (try
    (let [conn (m/make-connection "crossref" :host "127.0.0.1" :port 27017)
          databases (m/with-mongo conn
                      (m/databases))]
      (m/close-connection conn)
      databases)
    (catch Exception e
      false)))

(defn start []
  (let [result
        (sh "docker-compose" "up" "-d" "mongo" "solr"
            :env {"CAYENNE_SOLR_HOST" "cayenne_solr_1:8983"
                  "PATH" (System/getenv "PATH")
                  "MONGO_HOST" "cayenne_mongo_1:27017"})]
    (if-not (-> result :exit zero?)
      (do (println "Error starting Docker Compose:")
          (println result))
      (do
        ;; wait for solr to start, sometimes takes a while to load the core
        (while (or (not (solr-ready?))
                   (not (mongo-ready?)))
          (println "Waiting for solr and mongo to be ready..")
          (Thread/sleep 500))
        (start-core! :default :api :feed-api)))))

(defn stop []
  (stop-core! :default)
  (Thread/sleep 2000)
  (sh "docker-compose" "down"))

(defn reset []
  (stop-core! :default)
  (start-core! :default :api :feed-api))

(defn load-test-funders []
  (with-core :default
    (->> (.getPath (resource "registry.rdf"))
         (str "file://")
         (set-param! [:location :cr-funder-registry])))
  (with-redefs
   [cayenne.tasks.funder/get-country-literal-name
    (fn [model node]
      (let [url (rdf/->uri (first (rdf/objects (select-country-stmts model node))))]
        (case url
          "http://sws.geonames.org/2921044/" "Germany"
          "http://sws.geonames.org/6252001/" "United States"
          "http://sws.geonames.org/2077456/" "Australia"
          "http://sws.geonames.org/337996/" "Ethiopia"
          "http://sws.geonames.org/1814991/" "China"
          "http://sws.geonames.org/2635167/" "United Kingdom"
          "http://sws.geonames.org/3144096/" "Norway"
          "http://sws.geonames.org/2661886/" "Sweden"
          "http://sws.geonames.org/1861060/" "Japan"
          url)))]
    (load-funders)))

(defn load-test-journals []
  (with-core :default
    (set-param! [:location :cr-titles-csv] (.getPath (resource "titles.csv"))))
  (load-journals))

(defn process-feed []
  (let [feed-dir (.getPath (resource "feeds"))
        feed-source-dir (str feed-dir "/corpus")
        feed-in-dir (str feed-dir "/feed-in")
        feed-processed-dir (str feed-dir "/feed-processed")
        feed-file-count (count (dir-seq-glob (path feed-source-dir) "*.body"))]
    (delete-dir feed-processed-dir)
    (delete-dir feed-in-dir)
    (copy-dir feed-source-dir feed-in-dir)
    (with-core :default
      (set-param! [:dir :data] feed-dir)
      (set-param! [:dir :test-data] feed-dir)
      (set-param! [:location :cr-titles-csv] (.getPath (resource "titles.csv")))
      (set-param! [:service :solr :insert-list-max-size] 0))
    (load-journals)
    (with-redefs
     [cayenne.tasks.publisher/get-member-list
      (fn get-member-list []
        (read-string (slurp (resource "get-member-list.edn"))))
      cayenne.tasks.publisher/get-prefix-info
      (fn get-prefix-info [prefix]
        (assoc (read-string (slurp (resource "get-prefix-info.edn"))) :value prefix))]
      (load-members)
      (start-insert-list-processing)
      (start-feed-processing)
      (while (not= (solr-doc-count) feed-file-count)
        (println "Waiting for solr to finish indexing....")
        (Thread/sleep 1000))
      (check-journals "journals")
      (check-members "members"))))

(defn setup-for-feeds []
  (let [feed-dir (.getPath (resource "feeds"))
        feed-source-dir (str feed-dir "/corpus")
        feed-in-dir (str feed-dir "/feed-in")
        feed-processed-dir (str feed-dir "/feed-processed")
        feed-file-count (count (dir-seq-glob (path feed-source-dir) "*.body"))]
    (delete-dir feed-processed-dir)
    (delete-dir feed-in-dir)
    (with-core :default
      (set-param! [:dir :data] feed-dir)
      (set-param! [:dir :test-data] feed-dir)
      (set-param! [:location :cr-titles-csv] (.getPath (resource "titles.csv")))
      (set-param! [:service :solr :insert-list-max-size] 0))))

(def system @cores)

(defn remove-feed-references []
  (let [feed-dir (.getPath (resource "feeds"))
        feed-corpus-dir (str feed-dir "/corpus")
        dois (->> (http/get "http://localhost:3000/works?rows=1000&select=DOI" {:as :json})
                  :body
                  :message
                  :items
                  (map :DOI))]
    (doseq [doi dois]
      (let [clean-file-path (str feed-corpus-dir "/crossref-unixsd-" (str (java.util.UUID/randomUUID)) ".body")]
        (println "Downloading clean version of " doi)
        ;;TODO: Replace {REPLACE-ME} with a valid pid for crossref.org
        (spit clean-file-path (slurp (str "https://www.crossref.org/openurl/?pid={REPLACE-ME}&noredirect=true&format=unixsd&id=doi:" doi)))
        (Thread/sleep 1000)
        (println "Saved clean version of" doi "to" clean-file-path)))))
