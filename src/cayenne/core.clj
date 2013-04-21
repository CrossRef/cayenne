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

(defn parse-oai-file [f]
  (oai/process-file unixref-record-parser (record-json-writer "out.txt") f))

(defn load-oai-file [f]
  (oai/process-file unixref-record-parser (record-neo-inserter) f))

(defn find-citations-like [name dir patt]
  (oai/process-dir 
   dir 
   :parser unixref-citation-parser 
   :task (matching-citation-finder name "match.log.txt" patt)))

(defn find-citations-like-in-file [name f patt]
  (oai/process-file unixref-citation-parser (matching-citation-finder name "match.log.txt" patt) f))

(defn find-standards-citations [dir]
  (let [patt #"^(ASTM [A-G]|ISO |IEC |ISO/IEC |EN |EN ISO |BS |BS ISO |BS EN ISO |IEEE [A-Z]?)[0-9]+((\.|-)[0-9]+)? ((\.|-)[0-9]+)?(:[0-9]{4})?"]
    (find-citations-like :find-standards dir patt)))

(defn find-standards-citations-loose [dir]
  (let [patt #"(ASTM [A-G]|ISO |IEC |ISO/IEC |EN |EN ISO |BS |BS ISO |BS EN ISO |IEEE [A-Z]?)[0-9]+((\.|-)[0-9]+)? ((\.|-)[0-9]+)?(:[0-9]{4})?"]
    (find-citations-like :find-standards-loose dir patt)))

