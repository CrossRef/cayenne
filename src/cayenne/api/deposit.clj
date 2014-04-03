(ns cayenne.api.deposit
  (:require [clojure.string :as string]
            [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi-id]
            [cayenne.data.deposit :as deposit-data]
            [clj-xpath.core :refer [$x $x:tag $x:text $x:text* $x:attrs $x:attrs* $x:node
                                    xml->doc]])
  (:import [java.util UUID]
           [java.io StringWriter]))

;; handle deposit dispatch based on deposited content type

(defrecord DepositContext [owner content-type object test pingback-url batch-id])

(defn make-deposit-context [object content-type owner test? pingback-url]
  (DepositContext.
   owner
   content-type
   object
   test?
   pingback-url
   (.toString (UUID/randomUUID))))

(defmulti deposit! :content-type)

(defn parse-xml [context]
  (assoc context :xml (-> context :object xml->doc)))

(defn parse-deposit-dois [context]
  (assoc context 
    :dois
    (->> context 
         :xml 
         ($x:text* "//doi_data/doi")
         (map (comp string/trim doi-id/normalize-long-doi)))))

(defn parse-partial-deposit-dois [context]
  (assoc context
    :dois
    (->> context
         :xml
         ($x:text* "//body/*/doi")
         (map (comp string/trim doi-id/normalize-long-doi)))))

(defn alter-xml-batch-id [context]
  (doto ($x:node "//head/doi_batch_id/text()[0]" (:xml context))
    (.setNodeValue (:batch-id context))))

(defn alter-email [context]
  (doto ($x:node "//head/depositor/email_address/text()[0]" (:xml context))
    (.setNodeValue (conf/get-param [:deposit :email]))))

(defn create-deposit [context]
  (deposit-data/create!
   (.toString (:xml context))
   (:content-type context)
   (:batch-id context)
   (:dois context)
   (:owner context)
   (:test context)
   (:pingback-url context)))

(defmethod deposit! "application/vnd.crossref.deposit+xml" [context]
  (-> context
      parse-xml
    ;  parse-deposit-dois
    ;  alter-xml-batch-id
    ;  alter-email
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
  
