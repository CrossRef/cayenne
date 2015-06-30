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
            [clojure.java.io :refer [reader] :as io]
            [metrics.meters :refer [defmeter] :as meter])
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

(defmeter [cayenne feed files-received] "files-received")

(defn new-id [] (UUID/randomUUID))

(defn feed-in-dir []
  (str (conf/get-param [:dir :data]) "/feed-in"))

(defn feed-filename [copy-name content-type provider id]
  (str (conf/get-param [:dir :data])
       "/feed-" copy-name
       "/" provider
       "-" (content-type-mnemonics content-type)
       "-" id
       ".body"))

(defn parse-feed-filename [filename]
  (let [[provider content-type & rest] (-> filename
                                           (string/split #"/")
                                           last
                                           (string/split #"-"))
        id (-> (string/join "-" rest)
               (string/split #"\.")
               first)]
    {:content-type (content-type-mnemonics-reverse content-type)
     :provider provider
     :id id}))

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
    (.mkdirs (.getParentFile to-file))
    (.renameTo from-file to-file)))

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
        (process-fn rdr)
        (move-file! (:incoming-file feed-context)
                    (:processed-file feed-context)))
      (catch Exception e
        (move-file! (:incoming-file feed-context)
                    (:failed-file feed-context))))))
  
(defmulti process! :content-type)

(defmethod process! "application/vnd.datacite.datacite+xml" [feed-context]
  (process-with
   (fn [rdr]
     (let [f #(let [parsed (->> %
                                datacite/datacite-record-parser
                                (apply itree/centre-on))
                    with-source (assoc parsed
                                       :source
                                       (-> feed-context :provider provider-names))]
                (solr/insert-item with-source))]
       (xml/process-xml rdr "record" f)))
   feed-context))

(defmethod process! "application/vnd.crossref.unixsd+xml" [feed-context]
  (process-with
   (fn [rdr]
     (let [f #(let [parsed (->> %
                                unixsd/unixsd-record-parser
                                (apply category/apply-to)
                                (apply doaj/apply-to)
                                (apply funder/apply-to)
                                (apply itree/centre-on))
                    with-source (assoc parsed
                                       :source
                                       (-> feed-context :provider provider-names))]
                   (solr/insert-item with-source))]
       (xml/process-xml rdr "crossref_result" f)))
   feed-context))

(defn process-feed-files! []
  (doseq [f (-> (feed-in-dir) io/file .listFiles)]
    (-> f .getAbsolutePath make-feed-context process!)))

(defn record! [feed-context body]
  (let [incoming-file (-> feed-context :incoming-file io/file)]
    (.mkdirs (.getParentFile incoming-file))
    (io/copy body incoming-file)
    (meter/mark! files-received)))

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
  
