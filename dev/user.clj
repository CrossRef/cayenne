(ns user
  (:require [cayenne.conf :refer [cores start-core! stop-core!]]
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
    (when-not (-> result :exit zero?)
      (println "Error starting Docker Compose:")
      (println result)))


  ;; wait for solr to start, sometimes takes a while to load the core
  (while (or (not (solr-ready?))
             (not (mongo-ready?)))
    (println "Waiting for solr and mongo to be ready..")
    (Thread/sleep 500))
  (start-core! :default :api))

(defn stop []
  (stop-core! :default)
  (Thread/sleep 2000)
  (sh "docker-compose" "down"))

(defn reset []
  (stop-core! :default)
  (start-core! :default :api))

(def system @cores)
