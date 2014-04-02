(ns cayenne.api.deposit
  (:require [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip :as dzip]
            [clojure.data.zip.xml :as zx]
            [clojure.string :as string]
            [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi-id]
            [cayenne.data.deposit :as deposit-data])
  (:import [java.util UUID]
           [java.io StringWriter]))

;; handle deposit dispatch based on deposited content type

(defrecord DepositContext [owner content-type object test batch-id])

(defn make-deposit-context [object content-type owner test?]
  (DepositContext.
   owner
   content-type
   object
   test?
   (.toString (UUID/randomUUID))))

(defmulti deposit! :content-type)

(defn parse-xml [context]
  (assoc context :xml (-> context :object xml/parse zip/xml-zip)))

(defn parse-deposit-dois [context]
  (assoc context :dois
         (map (comp string/trim doi-id/normalize-long-doi)
          (zx/xml-> (:xml context) dzip/descendants :doi_data :doi zx/text))))

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

(defn alter-email [context]
  (let [email-loc (zx/xml1-> (:xml context) :head :depositor :email_address)
        new-xml (-> email-loc 
                    (zip/edit #(assoc % :content [(conf/get-param [:deposit :email])]))
                    zip/root
                    zip/xml-zip)]
    (assoc context :xml new-xml)))

(defn create-deposit [context]
  (let [string-writer (StringWriter.)
        xml-content (-> context :xml zip/root)]
    (xml/emit xml-content string-writer :encoding "UTF-8")
    (deposit-data/create!
     (-> string-writer (.toString) (.getBytes "UTF-8"))
     (:content-type context)
     (:batch-id context)
     (:dois context)
     (:owner context)
     (:test context))))

(defmethod deposit! "application/vnd.crossref.deposit+xml" [context]
  (-> context
      parse-xml
      parse-deposit-dois
      alter-xml-batch-id
      alter-email
      create-deposit)
  (:batch-id context))

(defmethod deposit! "application/vnd.crossref.partial+xml" [context]
  (-> context
      parse-xml
      parse-partial-deposit-dois
      alter-xml-batch-id
      alter-email
      create-deposit)
  (:batch-id context))
  
