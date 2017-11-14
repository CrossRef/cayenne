(ns cayenne.action
  (:import [java.net URLEncoder])
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [cayenne.conf :as conf]
            [cayenne.item-tree :as itree]
            [cayenne.ids.doi :as doi]
            [cayenne.xml :as xml]
            [cayenne.elastic.index :as es-index]
            [cayenne.formats.unixsd :refer [unixsd-record-parser]]
            [taoensso.timbre :as timbre :refer [info error]]))

(defn openurl-file [doi]
  (let [extracted-doi (doi/extract-long-doi doi)
        url (str (conf/get-param [:upstream :openurl-url]) 
                 (URLEncoder/encode extracted-doi))]
    (conf/remote-file url)))

(defn doi-file [doi]
  (let [extracted-doi (doi/extract-long-doi doi)
        url (str (conf/get-param [:upstream :doi-url])
                 (URLEncoder/encode extracted-doi))]
    (conf/remote-file url)))

(def print-itree-docs
  (comp
   #(info %)
   #(apply itree/centre-on %)))

(def print-elastic-docs
  (comp
   #(info %)
   es-index/index-command
   #(apply itree/centre-on %)))

(def index-elastic-docs
  (comp
   es-index/index-item
   #(apply itree/centre-on %)))

(defn process-file [file record-element using]
  (with-open [rdr (io/reader file)]
    (xml/process-xml
     rdr
     using
     record-element)))

(defn parse-unixsd-record [f using]
  (process-file (io/file f) "record" using))

(defn parse-doi [doi using]
  (process-file (doi-file doi) "crossref_result" using))

(defn parse-doi-list [list-file using]
  (with-open [rdr (io/reader (io/file list-file))]
    (doseq [doi (line-seq rdr)]
      (process-file (doi-file doi) "crossref_result" using))))
