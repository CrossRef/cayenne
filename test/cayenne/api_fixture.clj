(ns cayenne.api-fixture
  (:require [cayenne.api.v1.feed :refer [start-feed-processing]]
            [cayenne.conf :refer [start-core! stop-core! set-param! with-core]]
            [cayenne.tasks :refer [load-journals load-last-day-works]]
            [cayenne.tasks.coverage :refer [check-journals]]
            [cayenne.tasks.solr :refer [start-insert-list-processing]]
            [clojure.java.io :refer [resource]]
            [clojure.java.shell :refer [sh]]
            [clj-http.client :as http]
            [me.raynes.fs :refer [copy-dir delete-dir]]
            [nio2.io :refer [path]]
            [nio2.dir-seq :refer [dir-seq-glob]]
            [somnium.congomongo :as m]))

(defonce api-root "http://localhost:3000")

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
      (start-core! :default :api)
      (with-f)
      (f)
      (finally
        (stop-core! :default)
        (Thread/sleep 2000)
        (sh "docker-compose" "down")))))

(defn api-get [route]
  (let [message (-> (http/get (str api-root route) {:as :json})
                    :body
                    :message)]
    (cond-> message
      (:last-status-check-time message) (dissoc :last-status-check-time)
      (:indexed message) (dissoc :indexed)
      (:items message) (update :items (partial map #(dissoc % :indexed :last-status-check-time))))))

(def api-with-works
  (api-with 
    (fn []
      (let [feed-dir (.getPath (resource "feeds"))
            feed-source-dir (str feed-dir "/source")
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
        (start-insert-list-processing)
        (start-feed-processing)
        (while (not= (solr-doc-count) feed-file-count)
          (println "Waiting for solr to finish indexing....")
          (Thread/sleep 1000))
        (check-journals "journals")))))
