(ns cayenne.api-fixture
  (:require [cayenne.conf :refer [start-core! stop-core!]]
            [cayenne.tasks.funder :refer [select-country-stmts]]
            [cayenne.rdf :as rdf]
            [clojure.java.shell :refer [sh]]
            [clj-http.client :as http]
            [somnium.congomongo :as m]))

(defonce api-root "http://localhost:3000")

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

(defn api-with [with-f]
  (fn [f]
    (try 
      (sh 
        "docker-compose" "up" "-d" "mongo" "solr" 
        :env { "CAYENNE_SOLR_HOST" "cayenne_solr_1:8983" 
              "MONGO_HOST" "cayenne_mongo_1:27017"})
      ;; wait for solr to start, sometimes takes a while to load the core
      (while (or (not (solr-ready?))
                 (not (mongo-ready?)))
        (println "Waiting for solr and mongo to be ready..")
        (Thread/sleep 500))
      ;; TODO: move this to somewhere more obvious
      (intern 
        'cayenne.tasks.funder 
        'get-country-literal-name 
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
              url))))
      (start-core! :default :api)
      (with-f)
      (f)
      (finally
        (stop-core! :default)
        (Thread/sleep 2000)
        (sh "docker-compose" "down")))))
