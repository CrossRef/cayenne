(ns cayenne.api.deposit
  (:require [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip :as dzip]
            [clojure.data.zip.xml :as zx]
            [clojure.string :as string])
  (:import [java.util UUID]))

;; handle deposit dispatch based on deposited content type

(defrecord DepositContext [content-type object batch-id])

(defn make-deposit-context [content-type object]
  (DepositContext.
   content-type
   object
   (.toString (UUID/randomUUID))))

(defmulti deposit! :content-type)

(defn parse-xml [context]
  (assoc context :xml (-> context :object xml/parse zip/xml-zip)))

(defn parse-deposit-dois [context]
  (assoc context :dois
         (zx/xml-> (:xml context) dzip/descendants :doi_data :doi zx/text)))

(defn parse-partial-deposit-dois [context]
  (assoc context :dois
         (zx/xml-> (:xml context) :body dzip/children-auto :doi zx/text)))

(defn alter-xml-batch-id [context]
  (let [batch-doi-loc (zx/xml1-> (:xml context) :head :doi_batch_id)]
    (assoc context :xml
           (zip/xml-zip
            (zip/root
             (zip/edit batch-doi-loc
                       #(assoc % :content [(:batch-id context)])))))))

(defmethod deposit! "application/vnd.crossref.deposit+xml" [context]
  (-> context
      parse-xml
      parse-deposit-dois
      alter-xml-batch-id))

(defmethod deposit! "application/vnd.crossref.partial+xml" [context]
  (-> context
      parse-xml
      parse-partial-deposit-dois
      alter-xml-batch-id))
  
