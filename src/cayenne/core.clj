(ns cayenne.core
  (:use clojure.java.io)
  (:use cayenne.conf)
  (:use cayenne.sources.wok)
  (:use cayenne.tasks.dump)
  (:use cayenne.tasks.citations)
  (:use cayenne.tasks.neo4j)
  (:require [cayenne.oai :as oai])
  (:require [cayenne.html :as html])
  (:use [cayenne.formats.unixref :only [unixref-record-parser unixref-citation-parser]]))

(defn scrape-journal-short-names-from-wok []
  (html/scrape-urls journal-pages :scraper journal-names-scraper :task (record-writer "out.txt")))

(def j (file "/Users/karl/Projects/cayenne/test-data/j.xml"))
(def b (file "/Users/karl/Projects/cayenne/test-data/b.xml"))
(def s (file "/Users/karl/Projects/cayenne/test-data/s.xml"))

(defn parse-oai [file-or-dir]
  (oai/process
   file-or-dir
   :name :parse
   :parser unixref-record-parser 
   :task [record-json-writer "out.txt"]))

(defn load-oai [file-or-dir]
  (oai/process 
   file-or-dir
   :name :load
   :parser unixref-record-parser 
   :task [record-neo-inserter]))

(defn find-citations-like [file-or-dir patt]
  (oai/process
   file-or-dir
   :name :find-citations
   :parser unixref-citation-parser 
   :task [matching-citation-finder "match.log.txt" patt]))

(defn find-standards-citations [file-or-dir]
  (let [patt #"^(ASTM [A-G]|ISO |IEC |ISO/IEC |EN |EN ISO |BS |BS ISO |BS EN ISO |IEEE [A-Z]?)[0-9]+((\.|-)[0-9]+)? ((\.|-)[0-9]+)?(:[0-9]{4})?"]
    (find-citations-like file-or-dir patt)))

(defn find-standards-citations-loose [file-or-dir]
  (let [patt #"(ASTM [A-G]|ISO |IEC |ISO/IEC |EN |EN ISO |BS |BS ISO |BS EN ISO |IEEE [A-Z]?)[0-9]+((\.|-)[0-9]+)? ((\.|-)[0-9]+)?(:[0-9]{4})?"]
    (find-citations-like file-or-dir patt)))

