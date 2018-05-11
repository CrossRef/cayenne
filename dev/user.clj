(ns user
  (:require [cayenne.api.v1.feed :refer [start-feed-processing]]
            [cayenne.conf :refer [cores get-param set-param! start-core! stop-core! with-core]]
            [cayenne.elastic.convert :as elastic-convert]
            [cayenne.elastic.mappings :as elastic-mappings]
            [cayenne.rdf :as rdf]
            [cayenne.tasks :refer [index-journals index-members]]
            [cayenne.tasks.coverage :refer [check-journals check-members]]
            [cayenne.tasks.funder :refer [index-funders select-country-stmts]]
            [clj-http.client :as http]
            [clojure.java.io :refer [resource]]
            [clojure.java.shell :refer [sh]]
            [me.raynes.fs :refer [copy-dir delete-dir]]
            [nio2.dir-seq :refer [dir-seq-glob]]
            [nio2.io :refer [path]]))

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
  (sh "docker-compose" "down")
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
        (when-not @core-started?
          (when (start-core! :default :api :feed-api)
            (with-core :default
              (set-param! [:location :cr-titles-csv] (.getPath (resource "titles.csv")))
              (->> (.getPath (resource "registry.rdf"))
                   (str "file://")
                   (set-param! [:location :cr-funder-registry])))
            (reset! core-started? true)))))))

(defn stop []
  (sh "docker-compose" "down"))

(defn reset []
  (stop-core! :default)
  (start-core! :default :api :feed-api))

(defn load-test-funders []
  (println "Loading test funders from" (get-param [:location :cr-funder-registry]))
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

(defn setup-feed [& {:keys [source-dir] :or {source-dir "/corpus"}}]
  (let [feed-dir (.getPath (resource "feeds"))
        feed-source-dir (str feed-dir source-dir)
        feed-in-dir (str feed-dir "/feed-in")
        feed-processed-dir (str feed-dir "/feed-processed")
        feed-file-count (count (dir-seq-glob (path feed-source-dir) "*.body"))]
    (delete-dir feed-processed-dir)
    (delete-dir feed-in-dir)
    (with-core :default
      (set-param! [:dir :data] feed-dir)
      (set-param! [:dir :test-data] feed-dir)
      (set-param! [:location :cr-titles-csv] (.getPath (resource "titles.csv"))))
    (println "Feed source dir is " feed-source-dir)
    {:feed-source-dir feed-source-dir
     :feed-in-dir feed-in-dir
     :feed-file-count feed-file-count}))

(defn index-feed [& {:keys [source-dir]
                     :or {source-dir (or (System/getenv "CAYENNE_API_TEST_CORPUS") "/corpus")}}]
  (let [{:keys [feed-source-dir feed-in-dir feed-file-count]} (setup-feed :source-dir source-dir)]
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
      (Thread/sleep 1000)
      (let [doc-count (atom -1)]
        (while (< @doc-count (elastic-doc-count))
          (println "Waiting for elasticsearch to finish indexing....")
          (reset! doc-count (elastic-doc-count))
          (Thread/sleep 10000))
        (if (not= (elastic-doc-count) feed-file-count)
          (println "Gave up waiting for elasticsearch to finish indexing....")))
      (Thread/sleep 2000)
      (check-journals)
      (check-members)
      (Thread/sleep 2000))))

(defn elastic-work-hits []
  (map elastic-convert/es-doc->citeproc
       (-> (qbits.spandex/client {:hosts (get-param [:service :elastic :urls])})
           (qbits.spandex/request
            {:url [:work :_search]
             :method :get
             :body {:query {:match_all {}}}})
           :body :hits :hits)))

(defn spit-resource [path data]
  (spit (str "dev-resources/" path) (with-out-str (clojure.pprint/pprint data))))

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
