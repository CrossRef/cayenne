(ns cayenne.api.deposit
  (:require [clojure.string :as string]
            [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi-id]
            [cayenne.data.deposit :as deposit-data]
            [clj-xpath.core :refer [$x $x:tag $x:text $x:text* $x:text+ $x:attrs 
                                    $x:attrs* $x:node
                                    xml->doc node->xml with-namespace-context
                                    xmlnsmap-from-root-node]]
            [org.httpkit.client :as hc])
  (:import [java.util UUID]
           [java.io StringWriter]))

;; handle deposit dispatch based on deposited content type

(defrecord DepositContext [owner passwd content-type object test pingback-url batch-id])

(defn make-deposit-context [object content-type owner test? pingback-url]
  (DepositContext.
   owner
   passwd
   content-type
   object
   test?
   pingback-url
   (.toString (UUID/randomUUID))))

(defmulti deposit! :content-type)

(defn remove-children [node]
  (while (.hasChildNodes node)
    (.removeChild node (.getFirstChild node))))

(defn add-text [node doc text]
  (.appendChild node (.createTextNode doc text)))

(defn parse-xml [context]
  (let [root-node (-> context :object xml->doc)]
    (merge context
           {:xml root-node
            :namespaces (xmlnsmap-from-root-node root-node)})))

(defn parse-deposit-dois [context]
  (with-namespace-context (:namespaces context)
    (assoc context 
      :dois
      (->> context 
           :xml 
           ($x:text+ "//doi_data/doi")
           (map (comp string/trim doi-id/normalize-long-doi))))))

(defn parse-partial-deposit-dois [context]
  (assoc context
    :dois
    (->> context
         :xml
         ($x:text+ "//body/*/doi")
         (map (comp string/trim doi-id/normalize-long-doi)))))

(defn alter-xml-batch-id [context]
  (with-namespace-context (:namespaces context)
    (doto ($x:node "//doi_batch_id" (:xml context))
      (remove-children)
      (add-text (:xml context) (:batch-id context))))
  context)

(defn alter-email [context]
  (with-namespace-context (:namespaces context)
    (doto ($x:node "//depositor/email_address" (:xml context))
      (remove-children)
      (add-text (:xml context) (conf/get-param [:deposit :email]))))
  context)

(defn create-deposit [context]
  (deposit-data/create!
   (-> context :xml node->xml (.getBytes "UTF-8"))
   (:content-type context)
   (:batch-id context)
   (:dois context)
   (:owner context)
   (:test context)
   (:pingback-url context)))

(def type->deposit-operation 
  {"application/vnd.crossref.deposit+xml" "doMDUpload"
   "application/vnd.crossref.partial+xml" "doDOICitUpload"})

(defn perform-deposit [context]
  (doto (conf/get-service :executor)
    (.execute
     (fn []
       (let [post-url (if (:test? context) 
                        "http://test.crossref.org/servlet/deposit" 
                        "http://doi.crossref.org/servlet/deposit")
             operation (-> context :content-type type->deposit-operation)]
         (hc/post post-url
                  {:query-params {"operation" operation
                                  "login_id" (:owner context)
                                  "login_password" (:passwd context)}
                   :timeout 10000
                   :keepalive 10000
                   :multipart [{:name "fname"
                                :content (deposit-data/fetch-data {:id (:batch-id context)})
                                :filename (str (:batch-id context) ".xml")}]}))))))

(defmethod deposit! "application/vnd.crossref.deposit+xml" [context]
  (-> context
      parse-xml
      parse-deposit-dois
      alter-xml-batch-id
      alter-email
      create-deposit
      perform-deposit)
  (:batch-id context))

(defmethod deposit! "application/vnd.crossref.partial+xml" [context]
  (-> context
      parse-xml
      parse-partial-deposit-dois
      alter-xml-batch-id
      alter-email
      create-deposit
      perform-deposit)
  (:batch-id context))
