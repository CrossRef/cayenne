(ns cayenne.core
  (:use [clojure.java.io]
        [cayenne.conf]
        [cayenne.sources.wok]
        [cayenne.tasks.dump]
        [cayenne.tasks.citation]
        [clojure.tools.trace]
        [cayenne.formats.unixref :only [unixref-record-parser unixref-citation-parser]])
  ;;(:use cayenne.tasks.neo4j)
  (:require [cayenne.oai :as oai]
            [cayenne.html :as html]
            [cayenne.tasks.category :as cat]
            [cayenne.tasks.doaj :as doaj]
            [cayenne.item-tree :as itree]
            [cayenne.tasks.solr :as solr]))

(defn scrape-journal-short-names-from-wok []
  (html/scrape-urls journal-pages :scraper journal-names-scraper :task (record-writer "out.txt")))

(def j (file (str (get-param [:dir :test-data]) "/j.xml")))
(def b (file (str (get-param [:dir :test-data]) "/b.xml")))
(def s (file (str (get-param [:dir :test-data]) "/s.xml")))
(def funder-crossmark (file (str (get-param [:dir :test-data]) "/funder-crossmark.xml")))
(def funder-no-crossmark (file (str (get-param [:dir :test-data]) "/funder-no-crossmark.xml")))
(def orcid (file (str (get-param [:dir :test-data]) "/orcid.xml")))

(def dump-solr-docs
  (comp
   (record-json-writer "out.txt")
   solr/as-solr-document
   #(assoc % :source "CrossRef")
   #(apply itree/centre-on %)
   #(apply doaj/apply-to %)
   #(apply cat/apply-to %)))

(def index-solr-docs
  (comp 
   solr/insert-item
   #(assoc % :source "CrossRef")
   #(apply itree/centre-on %)
   #(apply doaj/apply-to %)
   #(apply cat/apply-to %)))

(defn parse-unixref [file-or-dir using]
  (oai/process file-or-dir
               :async false
               :name :parse
               :parser unixref-record-parser 
               :task using))

(defn get-unixref [service from until using]
  (oai/run service 
           :from from 
           :until until
           :parser unixref-record-parser
           :task using))

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

