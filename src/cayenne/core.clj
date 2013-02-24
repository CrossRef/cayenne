(ns cayenne.core
  (:import [java.util.concurrent Executors])
  (:require [cayenne.xml :as xml])
  (:use [clojure.java.io :only [file reader]])
  (:use [cayenne.html]))

; todo error reporting on pool-processing threads

(def debug-processing false)

(def processing-pool 
  (->
   (.. Runtime getRuntime availableProcessors)
   (+ 2)
   (* 3)))

(defn process-file [parser-fn task-fn file]
  (with-open [rdr (reader file)]
    (xml/process-xml rdr "record" (comp task-fn parser-fn))))

(defn process-file-in-pool [pool parser-fn task-fn file]
  (when debug-processing
    (prn (str "Executing " file)))
  (.execute pool #(process-file parser-fn task-fn file)))

(defn xml-file? [file]
  (and (.isFile file) (.endsWith (.getName file) ".xml")))

(defn xml-file-seq [dir count]
  (if (= count :all)
    (->> (file-seq dir)
         (filter xml-file?))
    (->> (file-seq dir)
         (filter xml-file?)
         (take count))))

(defn nothing [& rest] ())

(defn process-dir [dir & {:keys [count task parser after before async]
                          :or {async true
                               count :all
                               task nothing 
                               after nothing
                               before nothing}}]
  (doseq [file (xml-file-seq dir count)]
    (if async
      (process-file-in-pool processing-pool parser task file)
      (process-file parser task file))))

(defn scrape-url [scraper-fn task-fn url]
  (task-fn (scraper-fn (fetch-url url))))

(defn scrape-url-in-pool [pool scraper-fn task-fn url]
  (when debug-processing
    (prn (str "Executing " file)))
  (.execute pool #(scrape-url scraper-fn task-fn url)))

(defn scrape-urls [url-list & {:keys [count task scraper after before async]
                               :or {async true
                                    count :all
                                    task nothing 
                                    after nothing
                                    before nothing}}]
  (doseq [url url-list]
    (if async
      (scrape-url-in-pool processing-pool scraper task url)
      (scrape-url scraper task url))))
