(ns cayenne.core
  (:use clojure.java.io)
  (:use cayenne.conf)
  (:use cayenne.sources.wok)
  (:use cayenne.tasks.dump)
  (:require [cayenne.oai :as oai])
  (:require [cayenne.html :as html])
  (:use [cayenne.formats.unixref :only [unixref-record-parser unixref-simple-record-parser]]))

(defn scrape-journal-short-names-from-wok []
  (html/scrape-urls journal-pages :scraper journal-names-scraper :task (record-writer "out.txt")))

;(defn process-downloaded-unixref []
;  (let [oai-dir (conf-get [:oai :dir])
;        task (citations-neo4j-writer (conf-get [:neo4j :path]))]
;    (process-dir oai-dir :parser unixref-record-parser :task task)))

(def j (file "/Users/karl/Projects/cayenne/test-data/j.xml"))
(def b (file "/Users/karl/Projects/cayenne/test-data/b.xml"))
(def s (file "/Users/karl/Projects/cayenne/test-data/s.xml"))

(defn parse-oai-file [f]
  (oai/process-file unixref-record-parser (record-json-writer "out.txt") f))

