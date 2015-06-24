(ns cayenne.api.v1.feed
  (:require [cayenne.conf :as conf]
            [cayenne.xml :as xml]
            [cayenne.formats.unixsd :as unixsd]
            [cayenne.formats.datacite :as datacite]
            [cayenne.item-tree :as itree]
            [cayenne.tasks.funder :as funder]
            [cayenne.tasks.doaj :as doaj]
            [cayenne.tasks.category :as category]
            [cayenne.tasks.solr :as solr]
            [cayenne.api.v1.types :as types]
            [compojure.core :refer [defroutes ANY]]
            [liberator.core :refer [defresource]]
            [clojure.string :as string]
            [clojure.java.io :refer [reader] :as io])
  (:import [java.util UUID]
           [java.io File]))

(def feed-content-types #{"application/vnd.crossref.unixsd+xml"
                          "application/vnd.datacite.datacite+xml"})

(def content-type-mnemonics
  {"application/vnd.crossref.unixsd+xml" "unixsd"
   "application/vnd.datacite.datacite+xml" "datacite"})

(def content-type-mnemonics-reverse
  {"unixsd" "application/vnd.crossref.unixsd+xml"
   "datacite" "application/vnd.datacite.datacite+xml"})

(def feed-providers #{"crossref" "datacite"})

(def provider-names {"crossref" "CrossRef"
                     "datacite" "DataCite"})

(defn new-id [] (UUID/randomUUID))

(defn feed-filename [copy-name content-type provider id]
  (str (conf/get-param [:dir :data])
       "/feed-" copy-name
       "/" provider
       "-" (content-type-mnemonics content-type)
       "-" id
       ".body"))

(defn parse-feed-filename [filename]
  (let [[provider content-type id] (-> filename
                                       (string/split #"/")
                                       last
                                       (string/split #"-"))]
    {:content-type (content-type-mnemonics-reverse content-type)
     :provider provider
     :id (first (string/split id #"\."))}))

(defn incoming-file [feed-context]
  (feed-filename "in"
                 (:content-type feed-context)
                 (:provider feed-context)
                 (:id feed-context)))

(defn processed-file [feed-context]
  (feed-filename "processed"
                 (:content-type feed-context)
                 (:provider feed-context)
                 (:id feed-context)))

(defn failed-file [feed-context]
  (feed-filename "failed"
                 (:content-type feed-context)
                 (:provider feed-context)
                 (:id feed-context)))

(defn move-file! [from to]
  (let [from-file (File. from)
        to-file (File. to)]
    (.mkdirs (.getParentFile to))
    (.renameTo from to)))

(defn make-feed-context
  ([content-type provider]
   (let [base {:content-type content-type
               :provider provider
               :id (new-id)}]
     (-> base
         (assoc :incoming-file (incoming-file base))
         (assoc :processed-file (processed-file base))
         (assoc :failed-file (failed-file base)))))
  ([filename]
   (let [base (parse-feed-filename filename)]
     (-> base
         (assoc :incoming-file (incoming-file base))
         (assoc :processed-file (processed-file base))
         (assoc :failed-file (failed-file base))))))

(defn process-with [process-fn feed-context]
  (with-open [rdr (reader (:incoming-file feed-context))]
    (try 
      (do
        (-> rdr process-fn solr/insert-item)
        (move-file! (:incoming-file feed-context)
                    (:processed-file feed-context)))
      (catch Exception e
        (move-file! (:incoming-file feed-context)
                    (:failed-file feed-context))))))
  
(defmulti process! :content-type)

(defmethod process! "application/vnd.datacite.datacite+xml" [feed-context]
  (process-with
   (fn [rdr]
     (-> (xml/process-xml rdr "record" datacite/datacite-record-parser)
         ;; itree centre on?
         (assoc :source (provider-names (:provider feed-context)))))
   feed-context))

(defmethod process! "application/vnd.crossref.unixsd+xml" [feed-context]
  (process-with
   (fn [rdr]
     (let [metadata (->> (xml/process-xml rdr "" unixsd/unixsd-record-parser)
                         (apply category/apply-to)
                         (apply doaj/apply-to)
                         (apply funder/apply-to)
                         (apply itree/centre-on))]
       (assoc metadata :source (-> feed-context :provider provider-names))))
   feed-context))

(defn record! [feed-context body]
  (let [incoming-file (-> feed-context :incoming-file io/file)]
    (.mkdirs (.getParentFile incoming-file))
    (io/copy body incoming-file)))

(defresource feed-resource [provider]
  :allowed-methods [:post :options]
  :available-media-types types/json
  :known-content-type? #(some #{(get-in % [:request :headers "content-type"])}
                              feed-content-types)
  :exists? (fn [_] (some #{provider} feed-providers))
  :post! #(-> (get-in % [:request :headers "content-type"])
              (make-feed-context provider)
              (record! (get-in % [:request :body]))))

(defroutes feed-api-routes
  (ANY "/:provider" [provider]
       (feed-resource provider)))
  
