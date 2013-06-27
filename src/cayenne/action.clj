(ns cayenne.action
  (:import [java.net URLEncoder])
  (:use [cayenne.conf]
        [cayenne.sources.wok]
        [cayenne.tasks.dump]
        [cayenne.tasks.citation]
        [clojure.tools.trace]
        [cayenne.formats.unixref :only [unixref-record-parser unixref-citation-parser]]
        [cayenne.formats.datacite :only [datacite-record-parser]])
  ;;(:use cayenne.tasks.neo4j)
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [cayenne.oai :as oai]
            [cayenne.job :as job]
            [cayenne.html :as html]
            [cayenne.tasks.category :as cat]
            [cayenne.tasks.doaj :as doaj]
            [cayenne.tasks.funder :as funder]
            [cayenne.item-tree :as itree]
            [cayenne.tasks.solr :as solr]
            [cayenne.ids.doi :as doi]))

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

(def dump-plain-docs
  (record-json-writer "out.txt"))

(def dump-annotated-docs
  (comp
   (record-json-writer "out.txt")
   #(apply funder/apply-to %)
   #(apply doaj/apply-to %)
   #(apply cat/apply-to %)))

(def dump-solr-docs
  (comp
   (record-json-writer "out.txt")
   solr/as-solr-document
   #(assoc % :source "CrossRef")
   #(apply itree/centre-on %)
   #(apply funder/apply-to %)
   #(apply doaj/apply-to %)
   #(apply cat/apply-to %)))

(def index-solr-docs
  (comp 
   solr/insert-item
   #(assoc % :source "CrossRef")
   #(apply itree/centre-on %)
   #(apply funder/apply-to %)
   #(apply doaj/apply-to %)
   #(apply cat/apply-to %)))

(defn parse-unixref-records [file-or-dir using]
  (oai/process file-or-dir
               :async true
               :name :parse-unixref
               :split "record"
               :parser unixref-record-parser 
               :task using))

(defn parse-datacite-records [file-or-dir using]
  (oai/process file-or-dir
               :async true
               :name :parse-datacite
               :split "resource"
               :parser datacite-record-parser
               :task using))

(defn parse-openurl [doi using]
  (oai/process (openurl-file doi)
               :async true
               :name :parse-openurl
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
                   :name :parse-openurl-list
                   :kind ".tmp"
                   :split "doi_record"
                   :parser unixref-record-parser
                   :task (comp
                          using
                          #(vector (doi/to-long-doi-uri doi) (second %)))))))

(defn get-unixref-records [service from until using after]
  (when after (set-result-set-post! after))
  (oai/run-range service 
                 :from from 
                 :until until
                 :split "record"
                 :parser unixref-record-parser
                 :task using))

(defn reindex-fundref [funder-list-loc]
    (let [funder-info (-> funder-list-loc
                        (slurp)
                        (json/read-str))]
    (doseq [doi (get funder-info "items")]
      (parse-openurl doi index-solr-docs))
    (cayenne.tasks.solr/flush-insert-list)))

(defn check-url-citations [file-or-dir]
  (oai/process
   file-or-dir
   :name :check-url-citations
   :parser unixref-record-parser
   :task (url-citation-checker "check.log.txt")))

(defn find-citations-like [file-or-dir patt]
  (oai/process
   file-or-dir
   :name :find-citations
   :parser unixref-citation-parser 
   :task (matching-citation-finder "match.log.txt" patt)))

(defn find-standards-citations [file-or-dir]
  (let [patt #"^(ASTM [A-G]|ISO |IEC |ISO/IEC |EN |EN ISO |BS |BS ISO |BS EN ISO |IEEE [A-Z]?)[0-9]+((\.|-)[0-9]+)? ((\.|-)[0-9]+)?(:[0-9]{4})?"]
    (find-citations-like file-or-dir patt)))

(defn find-standards-citations-loose [file-or-dir]
  (let [patt #"(ASTM [A-G]|ISO |IEC |ISO/IEC |EN |EN ISO |BS |BS ISO |BS EN ISO |IEEE [A-Z]?)[0-9]+((\.|-)[0-9]+)? ((\.|-)[0-9]+)?(:[0-9]{4})?"]
    (find-citations-like file-or-dir patt)))

