(ns cayenne.core
  (:import [java.util.concurrent Executors])
  (:require [cayenne.xml :as xml])
  (:use [clojure.java.io :only [file reader]]))

; todo error reporting on pool-processing threads

(def debug-processing false)
(def pool-processing true)

(def processing-pool 
  (Executors/newFixedThreadPool (* 3 (+ 2 (.. Runtime getRuntime availableProcessors)))))

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

(defn process-dir [dir & {:keys [count task parser after before]
                          :or {count :all
                               task nothing 
                               after nothing
                               before nothing}}]
  (doseq [file (xml-file-seq dir count)]
    (if pool-processing
      (process-file-in-pool processing-pool parser task file)
      (process-file parser task file))))

