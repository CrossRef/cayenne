(ns user
  (:require [cayenne.conf :refer [get-param set-param! with-core cores start-core! stop-core!]]
            [cayenne.elastic.mappings :as elastic-mappings]
            [cayenne.elastic.convert :as elastic-convert]
            [cayenne.rdf :as rdf]
            [cayenne.tasks :refer [index-members index-journals]]
            [cayenne.tasks.funder :refer [select-country-stmts index-funders]]
            [cayenne.tasks.coverage :refer [check-journals check-members]]
            [cayenne.api.v1.feed :refer [start-feed-processing]]
            [clojure.java.io :refer [resource]]
            [clojure.java.shell :refer [sh]]
            [clj-http.client :as http]
            [me.raynes.fs :refer [copy-dir delete-dir]]
            [nio2.io :refer [path]]
            [nio2.dir-seq :refer [dir-seq-glob]]))

(defn- elastic-ready? []
  (try
    (= 200 (:status (http/get "http://localhost:9200")))
    (catch Exception e
      false)))

(defn elastic-doc-count []
  (-> (http/get "http://localhost:9200/work/_search" {:as :json})
      :body
      :hits
      :total))

(defn create-elastic-indexes []
  (elastic-mappings/create-indexes
   (qbits.spandex/client {:hosts (get-param [:service :elastic :urls])})))

(def core-started? (atom false))

(defn start []
  (let [result
        (sh "docker-compose" "up" "-d" "elasticsearch"
            :env {"PATH" (System/getenv "PATH")})]
    (if-not (-> result :exit zero?)
      (do (println "Error starting Docker Compose:")
          (println result))
      (do
        (while (not (elastic-ready?))
          (println "Waiting for elasticsearch to be ready..")
          (Thread/sleep 500))
        (create-elastic-indexes)
        (start-core! :default :api :feed-api)))))

(defn stop []
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
    (index-funders)))

(defn load-test-journals []
  (with-core :default
    (set-param! [:location :cr-titles-csv] (.getPath (resource "titles.csv"))))
  (index-journals))

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
      (set-param! [:location :cr-titles-csv] (.getPath (resource "titles.csv"))))
    {:feed-source-dir feed-source-dir
     :feed-in-dir feed-in-dir
     :feed-file-count feed-file-count}))

(defn process-feed []
  (let [{:keys [feed-source-dir feed-in-dir feed-file-count]} (setup-for-feeds)]
    (when-not (= feed-file-count 177)
      (throw (Exception.
              (str "The number of feed input files is not as expected. Expected to find "
                   177
                   " files in "
                   feed-source-dir
                   " but found "
                   feed-file-count))))
    (copy-dir feed-source-dir feed-in-dir)
    (index-journals)
    (with-redefs
     [cayenne.tasks.publisher/get-member-list
      (fn get-member-list []
        (read-string (slurp (resource "get-member-list.edn"))))
      cayenne.tasks.publisher/get-prefix-info
      (fn get-prefix-info [_ prefix]
        (assoc (read-string (slurp (resource "get-prefix-info.edn"))) :value prefix))]
      (index-members)
      (start-feed-processing)
      (while (not= (elastic-doc-count) 171)
        (println "Waiting for elasticsearch to finish indexing....")
        (Thread/sleep 1000))
      (check-journals)
      (check-members))))

(defn elastic-work-hits []
  (map elastic-convert/es-doc->citeproc
       (-> (qbits.spandex/client {:hosts (get-param [:service :elastic :urls])})
           (qbits.spandex/request
            {:url [:work :_search]
             :method :get
             :body {:query {:match_all {}}}})
           :body :hits :hits)))

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
