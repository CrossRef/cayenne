(ns cayenne.oai
  (:require [cayenne.xml :as xml]
            [cayenne.conf :as conf]
            [cayenne.job :as job]
            [clj-http.client :as client]
            [clj-time.core :as ctime]
            [clj-time.periodic :as ptime]
            [clj-time.format :as ftime]
            [clojure.string :as string]
            [clojure.java.io :refer [file reader writer]]
            [taoensso.timbre :as timbre :refer [info error]])
  (:use [cayenne.util])
  (:use [clojure.tools.trace]))

(defn ex->info-str [ex] (str ex ": " (first (.getStackTrace ex))))

(defn oai-success-handler [at-msg]
  (fn [job meta]
    (info {:info meta :at at-msg :complete true})))

(defn oai-exception-handler [at-msg]
  (fn [job meta ex]
    (error {:exception (ex->info-str ex) :info meta :at at-msg})))

(defn make-oai-job [at-msg func]
  (job/make-job func
                :success (oai-success-handler at-msg)
                :exception (oai-exception-handler at-msg)))

(defn parser-task-pass
  "If the parser doesn't support a record (returns nil)
   we skip the task fn."
  [task-fn parser-fn]
  (fn [record]
    (let [parsed-record (parser-fn record)]
      (if-not (nil? (second parsed-record))
        (task-fn parsed-record)
        (throw (Exception. "Parsed 0 records from an OAI file."))))))

(defn process-oai-xml-file
  "Run a parser and task over a file."
  [parser-fn task-fn file split]
  (with-open [rdr (reader file)]
    (xml/process-xml rdr split (parser-task-pass task-fn parser-fn))))

(defn process-oai-xml-file-async
  "Asynchronously run a parser and task over a file"
  [parser-fn task-fn file split]
  (let [job-func #(process-oai-xml-file parser-fn task-fn file split)
        job (make-oai-job :parsing job-func)
        job-meta {:file (str file)}]
    (job/put-job job-meta job)))

(defn resumption-token
  "Cheap and cheerful grab of resumption token."
  [body]
  (second (re-find #"resumptionToken=\"([^\"]+)\"" body)))

(declare grab-oai-xml-file-async)
(declare grab-oai-retry-token)
(declare grab-oai-retry-request)

(def max-retry-window (* 60 60 24))

(defn grab-oai-xml-file [service from until count token task-fn
                         & {:keys [last-retry-window]
                            :or {last-retry-window 10}}]
  (let [dir-name (str (or from "all") "-" (or until "all"))
        dir-path (file (:dir service) dir-name)
        file-name (str count "-" (or token "no-token") ".xml")
        xml-file (file dir-path file-name)
        params (if token
                 {"resumptionToken" token
                  "metadataPrefix" (:type service)}
                 (-> {"metadataPrefix" (:type service)
                      "verb" "ListRecords"}
                     (?> #(:set-spec service) assoc "set" (:set-spec service))
                     (?> from assoc "from" from)
                     (?> until assoc "until" until)))]
    (let [conn-mgr (conf/get-service :conn-mgr)
          resp (try (client/get (:url service) {:query-params params
                                                :throw-exceptions false
                                                :connection-manager conn-mgr})
                    (catch Exception e {:exception e}))]
      (if (or (:exception resp) (not (client/success? resp)))
        (let [err (or (:exception resp) (:status resp))]
          (cond
           (< last-retry-window max-retry-window)
           (do
             (grab-oai-retry-token service from until count token task-fn last-retry-window)
             (throw (Exception. (str "Bad response from OAI server: " err))))
           :else
           (do 
             (grab-oai-retry-request service from until task-fn)
             (throw (Exception. (str "Bad response from OAI server: " err))))))
        (do
          (.mkdirs dir-path)
          (spit xml-file (:body resp))
          (when (and (:parser service) task-fn)
            (process-oai-xml-file (:parser service) task-fn xml-file (:split service)))
          (when-let [token (resumption-token (:body resp))]
            (recur service from until (inc count) token task-fn 
                   (seq [:last-retry-window last-retry-window]))))))))

(defn grab-oai-retry-request [service from until task-fn]
  (let [job-func #(grab-oai-xml-file service from until 0 nil task-fn)
        job (make-oai-job :download job-func)
        job-meta {:service service
                  :from from
                  :until until 
                  :resumption-token nil}]
    (error 
     (str "Retrying resumption token download failed for longer than a day."
          "Skipping for a day. "
          job-meta))
    (job/put-job job-meta job :delay (* 60 60 24))))

(defn grab-oai-retry-token [service from until count token task-fn last-retry-window]
  (let [delay (* 2 last-retry-window)
        job-func #(grab-oai-xml-file service from until count token task-fn
                                     :last-retry-window delay)
        job (make-oai-job :download job-func)
        job-meta {:service service
                  :from from
                  :until until 
                  :resumption-token token}]
    (error
     (str "Retrying a failed resumption token download in " delay " seconds. "
          job-meta))
    (job/put-job job-meta job :delay delay)))

(defn grab-oai-xml-file-async [service from until count token task-fn]
  (let [job-func #(grab-oai-xml-file service from until count token task-fn)
        job (make-oai-job :download job-func)
        job-meta {:service service
                  :from from
                  :until until 
                  :resumption-token token}]
    (job/put-job job-meta job)))

(defn process
  "Invoke many process-oai-xml-file or process-oai-xml-file-async calls,
   one for each xml file under dir."
  [file-or-dir & {:keys [count task parser after before async kind split]
                  :or {kind ".xml"
                       async true
                       count :all
                       split "record"
                       task [constantly nil]
                       after (constantly nil)
                       before (constantly nil)}}]
  (doseq [file (file-kind-seq kind file-or-dir count)]
    (if async
      (process-oai-xml-file-async parser task file split)
      (process-oai-xml-file parser task file split))))

(defn run [service & {:keys [from until task]
                            :or {task nil}}]
  (grab-oai-xml-file-async service from until 1 nil task))

(defn str-date->parts [d]
  (map #(Integer/parseInt %) (string/split d #"-")))

(def oai-date-format (ftime/formatter "yyyy-MM-dd"))

(defn run-range [service & {:keys [from until task separation]
                            :or {task nil
                                 separation (ctime/days (:interval service))}}]
  (let [from-date (apply ctime/date-time (str-date->parts from))
        until-date (apply ctime/date-time (str-date->parts until))]
    (doseq [from-point (take-while #(ctime/before? % until-date)
                                   (ptime/periodic-seq from-date separation))]
      (run service
           :from (ftime/unparse oai-date-format from-point)
           :until (ftime/unparse oai-date-format (ctime/plus from-point separation))
           :task task))))
