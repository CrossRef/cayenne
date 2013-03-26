(ns cayenne.oai
  (:require [cayenne.xml :as xml])
  (:use [clojure.java.io :only [file reader]])
  (:use [cayenne.job]))

(def debug-processing false)

(defn process-file [parser-fn task-fn file]
  "Run a parser and task over a file."
  (with-open [rdr (reader file)]
    (xml/process-xml rdr "record" (comp task-fn parser-fn))))

(defn process-file-in-pool [pool parser-fn task-fn file]
  "Asynchronously run a parser and task over a file"
  (when debug-processing
    (prn (str "Executing " file)))
  (put-job #(process-file parser-fn task-fn file)))

(defn file-of-kind? [kind path]
  "Does the path point to a file that ends with .xml?"
  (and (.isFile path) (.endsWith (.getName path) kind)))

(defn file-kind-seq [kind dir count]
  "Return a seq of all xml files under the given directory."
  (if (= count :all)
    (->> (file-seq dir)
         (filter #(file-of-kind? kind %)))
    (->> (file-seq dir)
         (filter #(file-of-kind? kind %))
         (take count))))

(defn nothing [& rest] ())

(defn process-dir [dir & {:keys [count task parser after before async kind]
                          :or {kind ".xml"
                               async true
                               count :all
                               task nothing 
                               after nothing
                               before nothing}}]
  "Invoke many process-file or process-file-in-pool calls, one for each xml 
   file under dir."
  (doseq [file (file-kind-seq kind dir count)]
    (if async
      (process-file-in-pool processing-pool parser task file)
      (process-file parser task file))))
