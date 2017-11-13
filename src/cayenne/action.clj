(ns cayenne.action
  (:import [java.net URLEncoder])
  (:use [cayenne.conf]
        [cayenne.sources.wok]
        [cayenne.tasks.dump]
        [clojure.tools.trace]
        [cayenne.formats.unixref :only [unixref-record-parser unixref-citation-parser]]
        [cayenne.formats.unixsd :only [unixsd-record-parser]]
        [cayenne.formats.datacite :only [datacite-record-parser]])
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [cayenne.oai :as oai]
            [cayenne.job :as job]
            [cayenne.html :as html]
            [cayenne.tasks.funder :as funder]
            [cayenne.tasks.mongo :as mongo]
            [cayenne.item-tree :as itree]
            [cayenne.tasks.solr :as solr]
            [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi]
            [cayenne.elastic.index :as es-index]
            [taoensso.timbre :as timbre :refer [info error]]))

(conf/with-core :default
  (conf/set-param! [:oai :crossref-test :dir] (str (get-param [:dir :data]) "/oai/crossref-test"))
  (conf/set-param! [:oai :crossref-test :url] "http://oai.crossref.org/OAIHandler")
  (conf/set-param! [:oai :crossref-test :type] "cr_unixsd")
  (conf/set-param! [:oai :crossref-test :set-spec] "J:10.5555")

  (conf/set-param! [:oai :crossref-journals :dir] (str (get-param [:dir :data]) "/oai/crossref-journals"))
  (conf/set-param! [:oai :crossref-journals :url] "http://oai.crossref.org/OAIHandler")
  (conf/set-param! [:oai :crossref-journals :type] "cr_unixsd")
  (conf/set-param! [:oai :crossref-journals :set-spec] "J")
  (conf/set-param! [:oai :crossref-journals :interval] 7)
  (conf/set-param! [:oai :crossref-journals :split] "record")
  (conf/set-param! [:oai :crossref-journals :parser] cayenne.formats.unixsd/unixsd-record-parser)

  (conf/set-param! [:oai :crossref-books :dir] (str (get-param [:dir :data]) "/oai/crossref-books"))
  (conf/set-param! [:oai :crossref-books :url] "http://oai.crossref.org/OAIHandler")
  (conf/set-param! [:oai :crossref-books :type] "cr_unixsd")
  (conf/set-param! [:oai :crossref-books :set-spec] "B")
  (conf/set-param! [:oai :crossref-books :interval] 7)
  (conf/set-param! [:oai :crossref-books :split] "record")
  (conf/set-param! [:oai :crossref-books :parser] cayenne.formats.unixsd/unixsd-record-parser)

  (conf/set-param! [:oai :crossref-serials :dir] (str (get-param [:dir :data]) "/oai/crossref-serials"))
  (conf/set-param! [:oai :crossref-serials :url] "http://oai.crossref.org/OAIHandler")
  (conf/set-param! [:oai :crossref-serials :type] "cr_unixsd")
  (conf/set-param! [:oai :crossref-serials :set-spec] "S")
  (conf/set-param! [:oai :crossref-serials :interval] 7)
  (conf/set-param! [:oai :crossref-serials :split] "record")
  (conf/set-param! [:oai :crossref-serials :parser] cayenne.formats.unixsd/unixsd-record-parser)

  (conf/set-param! [:oai :datacite :dir] (str (get-param [:dir :data]) "/oai/datacite"))
  (conf/set-param! [:oai :datacite :url] "http://oai.datacite.org/oai")
  (conf/set-param! [:oai :datacite :type] "datacite")
  (conf/set-param! [:oai :datacite :interval] 7)
  (conf/set-param! [:oai :datacite :split] "resource")
  (conf/set-param! [:oai :datacite :parser] cayenne.formats.datacite/datacite-record-parser))

(defn scrape-journal-short-names-from-wok []
  (html/scrape-urls 
   journal-pages 
   :scraper journal-names-scraper 
   :task (record-writer "out.txt")))

(defn openurl-file [doi]
  (let [extracted-doi (doi/extract-long-doi doi)
        url (str (get-param [:upstream :openurl-url]) 
                 (URLEncoder/encode extracted-doi))]
    (remote-file url)))

(defn doi-file [doi]
  (let [extracted-doi (doi/extract-long-doi doi)
        url (str (get-param [:upstream :doi-url])
                 (URLEncoder/encode extracted-doi))]
    (remote-file url)))

(defn return-item [p]
  (fn [record] (deliver p record)))

(def dump-plain-docs
  (record-json-writer "out.txt"))

(def dump-annotated-docs
  (comp
   (record-json-writer "out.txt")
   #(apply funder/apply-to %)))

(def dump-solr-docs
  (comp
   (record-json-writer "out.txt")
   solr/as-solr-document
   #(assoc % :source "CrossRef")
   #(apply itree/centre-on %)
   #(apply funder/apply-to %)))

(def dump-plain-es-docs
  (comp
   (record-json-writer "out.txt")
   es-index/index-command
   #(apply itree/centre-on %)))

(def dump-plain-solr-docs
  (comp
   (record-json-writer "out.txt")
   solr/as-solr-document
   #(assoc % :source "CrossRef")
   #(apply itree/centre-on %)))

(def print-es-docs
  (comp
   #(info %)
   es-index/index-command
   #(assoc % :source "CrossRef")
   #(apply itree/centre-on %)
   #(apply funder/apply-to %)))

(def print-solr-docs
  (comp
   #(info %)
   solr/as-solr-document
   #(assoc % :source "CrossRef")
   #(apply itree/centre-on %)
   #(apply funder/apply-to %)))

(def index-solr-docs
  (comp 
   solr/insert-item
   #(assoc % :source "CrossRef")
   #(apply itree/centre-on %)
   #(apply funder/apply-to %)))

(def index-es-docs
  (comp
   es-index/index-item
   #(apply itree/centre-on %)))

(def store-item
  (comp
   (partial mongo/insert-item "items")
   #(assoc % :source "CrossRef")
   #(apply itree/centre-on %)
   #(apply funder/apply-to %)))

(defn parse-unixref-records [file-or-dir using]
  (oai/process file-or-dir
               :async false
               :split "record"
               :parser unixref-record-parser 
               :task using))

(defn parse-unixsd-records [file-or-dir using]
  (oai/process file-or-dir
               :async false
               :split "record"
               :parser unixsd-record-parser
               :task using))

(defn parse-unixsd-query-records [file-or-dir using]
  (oai/process file-or-dir
               :async false
               :kind ".tmp"
               :split "crossref_result"
               :parser unixsd-record-parser
               :task using))

(defn parse-datacite-records [file-or-dir using]
  (oai/process file-or-dir
               :async false
               :split "resource"
               :parser datacite-record-parser
               :task using))

(defn parse-openurl [doi using]
  (oai/process (openurl-file doi)
               :async true
               :kind ".tmp"
               :split "doi_record"
               :parser unixref-record-parser
               :task (comp 
                      using
                      #(vector (doi/to-long-doi-uri doi) (second %)))))

(defn parse-openurl-list [list-file using]
  (with-open [rdr (io/reader (io/file list-file))]
    (doseq [doi (line-seq rdr)]
      (oai/process (openurl-file doi)
                   :async true
                   :kind ".tmp"
                   :split "doi_record"
                   :parser unixref-record-parser
                   :task (comp
                          using
                          #(vector (doi/to-long-doi-uri doi) (second %)))))))

(defn parse-doi [doi using]
  (oai/process (doi-file doi)
               :async false
               :kind ".tmp"
               :split "crossref_result"
               :parser unixsd-record-parser
               :task (comp
                      using
                      #(vector (doi/to-long-doi-uri doi) (second %)))))

(defn parse-doi-list [list-file using]
  (with-open [rdr (io/reader (io/file list-file))]
    (doseq [doi (line-seq rdr)]
      (oai/process (doi-file doi)
                   :async true
                   :kind ".tmp"
                   :split "crossref_result"
                   :parser unixsd-record-parser
                   :task (comp
                          using
                          #(vector (doi/to-long-doi-uri doi) (second %)))))))

(defn get-oai-records [service from until using]
  (oai/run-range service
                 :from from 
                 :until until
                 :task using))

(defn reindex-fundref [funder-list-loc]
    (let [funder-info (-> funder-list-loc
                        (slurp)
                        (json/read-str))]
    (doseq [doi (get funder-info "items")]
      (parse-openurl doi index-solr-docs))
    (cayenne.tasks.solr/flush-insert-list)))

(defn rerun-oai-service [service from until action]
  (let [existing-dir (clojure.java.io/file (:dir service)
                                           (str from "-" until))]
    (doseq [f (.listFiles existing-dir)]
      (.delete f))
    (get-oai-records service from until action)))

(defn rerun-cr-failed 
  "Retry a failed CrossRef OAI-PMH download represented by a fail log line."
  [log-line action]
  (if (re-find #":file " log-line)

    (let [path (second (re-find #":file ([\w/-]+)" log-line))
          path-split (reverse (string/split path #"/"))
          from-until (string/split (second path-split) #"-")
          from (string/join "-" (take 3 from-until))
          until (string/join "-" (take 3 (drop 3 from-until)))
          service (conf/get-param [:oai (keyword (nth path-split 2))])]
      (rerun-oai-service service from until action))

    (let [from (second (re-find #":from ([0-9-]+)" log-line))
          until (second (re-find #":until ([0-9-]+)" log-line))
          service (condp = (second (re-find #":set-spec (J|S|B)" log-line))
                    "J" (conf/get-param [:oai :crossref-journals])
                    "S" (conf/get-param [:oai :crossref-serials])
                    "B" (conf/get-param [:oai :crossref-books]))]
      (rerun-oai-service service from until action))))

(defn rerun-all-cr-failed 
  "Retry CrossRef OAI-PMH download of each failed download in a log file."
  [log-file action]
  (with-open [rdr (clojure.java.io/reader log-file)]
    (doseq [log-line (line-seq rdr)]
      (when (re-find #":state :fail" log-line)
        (rerun-cr-failed log-line action)))))

(defn find-doi 
  "Look up a DOI via CrossRef Metadata Search."
  [doi]
  (let [search-url (-> (get-param [:upstream :crmds-dois]) 
                       (str doi) 
                       (java.net.URL.))
        response (-> search-url (slurp) (json/read-str))]
    [doi (not (empty? response))]))

(defn find-dois 
  "Look up a list of DOIs via CrossRef Metadata Search. Result is a map mapping
   DOI to CRMDS response."
  [doi-list-loc]
  (into {} (map find-doi (-> doi-list-loc (line-seq) (distinct)))))

(defn update-member-solr-docs [id offset to-be-updated?]
  (let [data (-> (java.net.URL. (str "http://api.crossref.org/v1/members/" 
                                     id 
                                     "/works?rows=1000&offset=" 
                                     offset))
                 slurp
                 json/read-str
                 (get-in ["message" "items"]))]
    (doseq [record data]
      (when (to-be-updated? record)
          (println (str "Running on " (get record "DOI")))
          (parse-doi (get record "DOI") index-solr-docs)))
    (when (pos? (count data))
      (println (str "Requesting from " (+ offset 1000)))
      (recur id (+ offset 1000) to-be-updated?))))



(defn elife-bad-container-title? [record]
  (> (count (get record "container-title")) 1))

(defn update-solr-docs-for-query [query]
  (let [dois (map 
              #(get % "DOI")
              (-> (java.net.URL. (str "http://api.crossref.org/v1/works?"
                                      query))
                  slurp
                  json/read-str
                  (get-in ["message" "items"])))]
    (println "Updating" (count dois) "DOIs")
    (doall (map #(do (println %) (parse-doi % index-solr-docs)) dois))
    (println "Done")))

(defn find-funder-entries-no-id* [member-id offset]
  (let [data (-> (java.net.URL.
                  (str "http://api.crossref.org/v1/members/"
                       member-id
                       "/works?rows=1000&filter=has-funder:true&offset="
                       offset))
                 slurp
                 (json/read-str :key-fn keyword)
                 (get-in [:message :items]))]
    (concat
     (mapcat
      (fn [record]
        (let [doi (:DOI record)
              funding (:funder record)]
          (->> funding
               (remove :DOI)
               (map #(vector doi (:name %))))))
      data)
     (if (zero? (count data))
       []
       (find-funder-entries-no-id* member-id (+ offset 1000))))))

(defn find-funder-entries-no-id [member-id]
  (filter (complement empty?) (find-funder-entries-no-id* member-id 0)))

