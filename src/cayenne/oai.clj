(ns cayenne.oai
  (:require [cayenne.xml :as xml]
            [cayenne.conf :as conf])
  (:use [clojure.java.io :only [file reader]])
  (:use [cayenne.job])
  (:use [cayenne.util]))

(def debug-processing true)

(defn process-oai-xml-file [parser-fn task file result-set]
  "Run a parser and task over a file."
  (conf/with-result-set result-set
    (with-open [rdr (reader file)]
      (let [task-fn (apply (first task) (rest task))]
        (xml/process-xml rdr "record" (comp task-fn parser-fn))))))

(defn process-oai-xml-file-async [parser-fn task file result-set]
  "Asynchronously run a parser and task over a file"
  (when debug-processing
    (prn (str "Executing " file)))
  (put-job #(process-oai-xml-file parser-fn task file result-set)))

(defn process [file-or-dir & {:keys [count task parser after before async kind name]
                              :or {kind ".xml"
                                   async true
                                   count :all
                                   task [constantly nil]
                                   after (constantly nil)
                                   before (constantly nil)}}]
  "Invoke many process-oai-xml-file or process-oai-xml-file-async calls, one for each xml 
   file under dir."
  (doseq [file (file-kind-seq kind file-or-dir count)]
    (if async
      (process-oai-xml-file-async parser task file name)
      (process-oai-xml-file parser task file name))))
