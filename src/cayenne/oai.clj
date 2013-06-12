(ns cayenne.oai
  (:require [cayenne.xml :as xml]
            [cayenne.conf :as conf]
            [cayenne.job :as job]
            [clj-http.client :as client]
            [clj-time.core :as ctime]
            [clj-time.periodic :as ptime]
            [clj-time.format :as ftime]
            [clojure.string :as string])
  (:use [clojure.java.io :only [file reader writer]])
  (:use [cayenne.util])
  (:use [clojure.tools.trace]))

(defn log-state [msg state]
  (conf/log
   {:message msg
    :state state
    :file (str (conf/get-result :file))
    :oai {:resumption-token (conf/get-result :resumption-token)
          :set-spec (conf/get-result :set-spec)
          :from (conf/get-result :from)
          :until (conf/get-result :until)
          :url (conf/get-result :url)}}))

(defn log-fail [msg] (log-state msg :fail))
(defn log-info [msg] (log-state msg :info))

(defn ex->info-str [ex] (str ex ": " (first (.getStackTrace ex))))

(defn parser-task-pass 
  "If the parser doesn't support a record (returns nil)
   we skip the task fn."
  [task-fn parser-fn]
  (fn [record]
    (let [parsed-record (parser-fn record)]
      (if-not (nil? (second parsed-record))
        (task-fn parsed-record)
        (log-fail "Parser returned nil for a record.")))))

(defn process-oai-xml-file 
  "Run a parser and task over a file."
  [parser-fn task-fn file result-set split]
  (conf/set-result! :file file)
  (log-info "Processing.")
  (with-open [rdr (reader file)]
    (xml/process-xml rdr split (parser-task-pass task-fn parser-fn))))

(defn process-oai-xml-file-async
  "Asynchronously run a parser and task over a file"
  [parser-fn task-fn file result-set split]
  (let [job (job/make-job #(process-oai-xml-file parser-fn task-fn file result-set split)
                      :fail #(log-fail "Failed to process OAI file.")
                      :exception #(log-fail (str "Failed to process OAI file due to: " (ex->info-str %3))))
        meta {:file (str file)}]
    (job/put-job result-set meta job)))

(defn resumption-token 
  "Cheap and cheerful grab of resumption token."
  [body]
  (second (re-find #"resumptionToken=\"([^\"]+)\"" body)))
  
(declare grab-oai-xml-file-async)

(defn grab-oai-xml-file [service from until count token parser-fn task-fn result-set]
  (let [dir-name (str from "-" until)
        dir-path (file (:dir service) dir-name)
        file-name (str count "-" (or token "no-token") ".xml")
        xml-file (file dir-path file-name)
        params (if token
                 {"resumptionToken" token}
                 (-> {"metadataPrefix" (:type service)
                      "verb" "ListRecords"}
                     (?> #(:set-spec service) assoc "setspec" (:set-spec service))
                     (?> from assoc "from" from)
                     (?> until assoc "until" until)))]
    (conf/set-result! :from from)
    (conf/set-result! :until until)
    (conf/set-result! :resumption-token token)
    (conf/set-result! :url (:url service))
    (conf/set-result! :set-spec (:set-spec service))
    (log-info "Downloading OAI-PMH file.")
    (let [conn-mgr (conf/get-service :conn-mgr)
          resp (client/get (:url service) {:query-params params
                                           :throw-exceptions false
                                           :connection-manager conn-mgr})]
      (if (not (client/success? resp))
        (log-fail (str "Bad response from OAI server: " (:status resp)))
        (do
          (.mkdirs dir-path)
          (spit xml-file (:body resp))
          (when parser-fn 
            (process-oai-xml-file parser-fn task-fn xml-file result-set "record"))
          (when-let [token (resumption-token (:body resp))]
            (recur service from until (inc count) token parser-fn task-fn result-set)))))))

(defn grab-oai-xml-file-async [service from until count token parser-fn task-fn result-set]
  (let [job (job/make-job #(grab-oai-xml-file service from until count token parser-fn task-fn result-set)
                          :fail #(log-fail "Failed to download OAI file")
                          :exception #(log-fail (str "Failed to download OAI file due to: " (ex->info-str %3))))
        meta {:file (str file)}]
    (job/put-job result-set meta job)))

(defn process 
  "Invoke many process-oai-xml-file or process-oai-xml-file-async calls, 
   one for each xml file under dir."
  [file-or-dir & {:keys [count task parser after before async kind name split]
                  :or {kind ".xml"
                       async true
                       count :all
                       split "record"
                       task [constantly nil]
                       after (constantly nil)
                       before (constantly nil)}}]
  (doseq [file (file-kind-seq kind file-or-dir count)]
    (if async
      (process-oai-xml-file-async parser task file name split)
      (process-oai-xml-file parser task file name split))))

(defn run [service & {:keys [from until task parser name]
                            :or {task nil
                                 parser nil}}]
  (grab-oai-xml-file-async service from until 1 nil parser task name))

(defn str-date->parts [d]
  (map #(Integer/parseInt %) (string/split d #"-")))

(def oai-date-format (ftime/formatter "yyyy-MM-dd"))

(defn run-range [service & {:keys [from until task parser name separation]
                            :or {task nil
                                 parser nil
                                 separation (ctime/days 7)}}]
  (let [from-date (apply ctime/date-time (str-date->parts from))
        until-date (apply ctime/date-time (str-date->parts until))]
    (doseq [from-point (take-while #(ctime/before? % until-date)
                                   (ptime/periodic-seq from-date separation))]
      (run service 
           :from (ftime/unparse oai-date-format from-point)
           :until (ftime/unparse oai-date-format (ctime/plus from-point separation))
           :task task 
           :parser parser 
           :name name))))
