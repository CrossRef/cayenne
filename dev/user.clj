(ns user
  (:require [cayenne.conf :refer [set-param! with-core cores start-core! stop-core!]]
            [cayenne.tasks :refer [load-funders]]
            [cayenne.rdf :as rdf]
            [cayenne.tasks :refer [load-journals]]
            [cayenne.tasks.funder :refer [select-country-stmts]]
            [clojure.java.io :refer [resource]]
            [clojure.java.shell :refer [sh]]
            [clj-http.client :as http]
            [somnium.congomongo :as m]))

(defn- solr-ready? []
  (try
    (= 200 (:status (http/get "http://localhost:8983/solr/crmds1/admin/ping")))
    (catch Exception e
      false)))

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
        (start-core! :default :api)))))

(defn stop []
  (stop-core! :default)
  (Thread/sleep 2000)
  (sh "docker-compose" "down"))

(defn reset []
  (stop-core! :default)
  (start-core! :default :api))

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

(def system @cores)
