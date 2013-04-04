(ns cayenne.core
  (:use clojure.java.io)
  (:use cayenne.conf)
  (:use cayenne.sources.wok)
  (:use cayenne.tasks.dump)
  (:use cayenne.tasks.neo4j)
  (:require [cayenne.oai :as oai])
  (:require [cayenne.html :as html])
  (:use [cayenne.formats.unixref :only [unixref-record-parser]]))

(defn scrape-journal-short-names-from-wok []
  (html/scrape-urls journal-pages :scraper journal-names-scraper :task (record-writer "out.txt")))

(def j (file "/Users/karl/Projects/cayenne/test-data/j.xml"))
(def b (file "/Users/karl/Projects/cayenne/test-data/b.xml"))
(def s (file "/Users/karl/Projects/cayenne/test-data/s.xml"))

(defn parse-oai-file [f]
  (oai/process-file unixref-record-parser (record-json-writer "out.txt") f))

(defn load-oai-file [f]
  (oai/process-file unixref-record-parser (record-neo-inserter) f))

