(ns user
  "User namespace for testing and development."
  (:require [cayenne.api.v1.feed :as feed :refer [start-feed-processing feed-thread-log]]
            [cayenne.conf :refer [cores get-param set-param! start-core! stop-core! with-core]]
            [cayenne.elastic.convert :as elastic-convert]
            [cayenne.elastic.mappings :as elastic-mappings]
            [cayenne.rdf :as rdf]
            [cayenne.tasks :refer [index-journals index-members]]
            [cayenne.tasks.coverage :refer [check-journals check-members]]
            [cayenne.tasks.funder :refer [index-funders select-country-stmts]]
            [clj-http.client :as http]
            [clojure.java.io :as io :refer [resource]]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as string]
            [me.raynes.fs :refer [copy-dir delete-dir]]
            [nio2.dir-seq :refer [dir-seq-glob]]
            [nio2.io :refer [path]]
            [qbits.spandex :as elastic]
            [robert.bruce :as bruce]))

(defn client []
  (qbits.spandex/client {:hosts (get-param [:service :elastic :urls])}))

(defn- elastic-ready? []
  (try
    (-> (client)
         (elastic/request {:url [:_cat :indices] :method :get})
         :status
         #{200}
        boolean)

    (catch Exception e
      false)))

(defn wait-for-elastic?
  "Wait for Elastic to become ready, return success."
  []
  (bruce/try-try-again {:tries 100 :sleep 10000} elastic-ready?))

(defn elastic-doc-count []
  (-> (client)
      (elastic/request {:url [:work :_search] :as :json})
      :body :hits :total))


(defn delete-elastic-indexes
  "Restore Elastic Search to a initial state between tests."
  ; Implemented in user because there's no good reason to do this in production.
  []
  (-> (client)
      (elastic/request {:url [:_all ] :method :delete})))


(defn flush-elastic
  "Wait for all Elastic Search indexing activity to complete before proceeding."
  []
  (-> (client)
      (elastic/request {:url [:_all :_flush]
                        :method :post
                        :query-string
                        {:wait_if_ongoing true
                         :force true}}))
  (-> (client)
      (elastic/request {:url [:_all :_refresh]
                        :method :post})))

(defn create-elastic-indexes []
  (elastic-mappings/create-indexes (client)))

(def core-started? (atom false))

(defn start []
  ; For easier debugging ingest only one at once.
  (set-param! [:val :feed-concurrency] 1)

  (set-param! [:service :elastic :urls] ["http://elasticsearch:9200"])

  (when-not (wait-for-elastic?)
    (println "Error: Can't start Elastic")
    (System/exit 1))

  ; Delete the Elastic index when starting, not stopping.
  ; This allows us to inspect it after tests for debugging.
  (delete-elastic-indexes)
  (create-elastic-indexes)

  (when (and (not @core-started?)
             (start-core! :default :api :feed-api))
    (reset! core-started? true)
    (with-core :default
      (set-param! [:location :cr-titles-csv] (.getPath (resource "titles.csv")))
      (->> (.getPath (resource "registry.rdf"))
           (str "file://")
           (set-param! [:location :cr-funder-registry])))))

(defn stop []
  ; TODO Maybe we don't need this any more.
  (println "Stopping!"))

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

(defn setup-feed
  "Build structure of corpus, feed in and feed out directories.
   Start by deleting the feed in and out directories.
   May be used either for testing or production.
   TODO? should this be part of cayenne.conf ?"
  [& {:keys [source-dir] :or {source-dir "/corpus"}}]
  (let [feed-dir (.getPath (resource "feeds"))

        ; source-dir is configurable or '/corpus'
        feed-source-dir (str feed-dir source-dir)

        ; feed-in is where the 'pusher' places files to be indexed in production.
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

(defn body-file-count
  [dir]
  (->> dir
       io/file
       file-seq
       (map #(.getName %))
       (filter #(clojure.string/ends-with? % ".body"))
       count))

(def core-started? (atom false))

(defn index-feed
  "Set up an instance with the test corpus indexed."
  [& {:keys [source-dir]
      :or {source-dir (or (System/getenv "CAYENNE_API_TEST_CORPUS") "/corpus")}}]

  (let [{:keys [feed-source-dir feed-in-dir feed-file-count]} (setup-feed :source-dir source-dir)
        num-feed-files (body-file-count feed-source-dir)]
    (println "Copying" num-feed-files "files of test data from" feed-source-dir "to" feed-in-dir)
    (copy-dir feed-source-dir feed-in-dir)
    (index-journals)
    (with-redefs
     [cayenne.tasks.publisher/get-member-list
      (fn get-member-list []
        (read-string (slurp (resource "get-member-list.edn"))))
      cayenne.tasks.publisher/get-prefix-info
      (fn get-prefix-info [_ prefix]
        (assoc (read-string (slurp (resource "get-prefix-info.edn"))) :value prefix))

      feed-thread-log println]
      (index-members)

      (feed/feed-once!)

      ; Wait for all docs to be indexed becuase we're going to query them in coverage.
      (println "Flush work indexes...")
      (flush-elastic)

      ; Build coverage.
      (println "Generate coverage for journals...")
        (check-journals)

      (println "Generate coverage for members...")
      (check-members)

      (println "Flush coverage indexes...")
      (flush-elastic)

      (println "Done creating instance."))))

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

