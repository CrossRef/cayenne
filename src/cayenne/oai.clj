(ns cayenne.oai
  (:require [cayenne.xml :as xml]
            [cayenne.conf :as conf]
            [clj-http.client :as client])
  (:use [clojure.java.io :only [file reader writer]])
  (:use [cayenne.job])
  (:use [cayenne.util])
  (:use [clojure.tools.trace]))

(def debug-processing true)
(def debug-grabbing true)

(defn process-oai-xml-file 
  "Run a parser and task over a file."
  [parser-fn task-fn file result-set]
  (conf/with-result-set result-set
    (with-open [rdr (reader file)]
      (xml/process-xml rdr "record" (comp task-fn parser-fn)))))

(defn process-oai-xml-file-async 
  "Asynchronously run a parser and task over a file"
  [parser-fn task-fn file result-set]
  (when debug-processing
    (prn (str "Executing " file)))
  (put-job result-set #(process-oai-xml-file parser-fn task-fn file result-set)))

(defn resumption-token 
  "Cheap and cheerful grab of resumption token."
  [body]
  (second (re-find "resumptionToken=\"([^\"]+)\"")))
  
(declare grab-oai-xml-file-async)

(defn grab-oai-xml-file [service from until token process-fn]
  (let [dir-name (str from "-" until)
        file-name (str (or token "first") ".xml")
        xml-file (file (:dir service) dir-name file-name)
        params (-> {"metadataPrefix" (:type service)
                    "verb" "ListRecords"}
                   (?> #(:set-spec service) assoc "setspec" (:set-spec service))
                   (?> from assoc "from" from)
                   (?> until assoc "until" until)
                   (?> token assoc "resumptionToken" token))]
    (let [conn-mgr (conf/get-service :conn-mgr)
          resp (client/get (:url service) {:query-params params
                                           :throw-exceptions false
                                           :connection-manager conn-mgr})]
      (when (client/success? resp)
        (.mkdirs xml-file)
        (with-open [xml-out (writer xml-file)]
          (.write xml-out (:body resp)))
        (when-let [token (resumption-token (:body resp))]
          (grab-oai-xml-file-async service from until token process-fn))
        (when process-fn 
          (process-fn xml-file))))))

(defn grab-oai-xml-file-async [service from until count token resumer-fn process-fn]
  (when debug-grabbing
    (prn (str "Grabbing " from " " until)))
  (put-job #(grab-oai-xml-file service from until count process-fn)))

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

(defn run [service & {:keys [from until task parser]
                            :or {task nil
                                 parser nil}}]
  ())

;; (oai/run (conf/get-param [:oai :crossref-journals])
;;          :from "2000-01-01"
;;          :until "2013-04-01"
;;          :parser unixref-record-parser
;;          :task (record-json-writer "out.txt"))

