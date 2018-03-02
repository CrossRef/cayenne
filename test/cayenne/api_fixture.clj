(ns cayenne.api-fixture
  (:require [cayenne.api.v1.feed :refer [start-feed-processing]]
            [cayenne.conf :refer [set-param! with-core]]
            [cayenne.tasks :refer [load-journals load-last-day-works]]
            [cayenne.tasks.coverage :refer [check-journals]]
            [cayenne.tasks.solr :refer [start-insert-list-processing]]
            [clojure.java.io :refer [resource]]
            [clj-http.client :as http]
            [me.raynes.fs :refer [copy-dir delete-dir]]
            [nio2.io :refer [path]]
            [nio2.dir-seq :refer [dir-seq-glob]]))

(defonce api-root "http://localhost:3000")

(defn solr-doc-count []
  (-> (http/get "http://localhost:8983/solr/admin/cores?action=STATUS&wt=json" {:as :json})
      :body
      :status
      :crmds1
      :index
      :numDocs))

(defn api-with [with-f]
  (fn [f]
    (try 
      (user/start) 
      (with-f)
      (f)
      (finally
        (user/stop)))))

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
