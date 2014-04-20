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
           [java.io StringWriter]
           [java.util.concurrent TimeUnit]))

;; handle deposit dispatch based on deposited content type

(defrecord DepositContext [owner passwd content-type object test pingback-url batch-id])

(defn make-deposit-context [object content-type owner passwd test? pingback-url]
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

(defn parse-xml-deposit-dois [context]
  (with-namespace-context (:namespaces context)
    (assoc context 
      :dois
      (->> context 
           :xml 
           ($x:text+ "//doi_data/doi")
           (map (comp string/trim doi-id/normalize-long-doi))))))

(defn parse-xml-partial-deposit-dois [context]
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

(defn alter-xml-email [context]
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
   (:passwd context)
   (:test context)
   (:pingback-url context))
  context)

(def type->deposit-operation 
  {"application/vnd.crossref.deposit+xml" "doMDUpload"
   "application/vnd.crossref.partial+xml" "doDOICitUpload"})

(defn expo-seconds-delay [_ x]
  (if (> x 10)
    :never
    (* (Math/exp x) 1000)))

(defn perform-xml-deposit-handoff 
  "Either successfully hands off a deposit to the DS or throws an
   exception. data-source is either :remote - get data stored in mongo,
   or a key that keys data in the context."
  [context & {:keys [data-source] :or {data-source :remote}}]
  (let [data-object (if (= data-source :remote)
                      (deposit-data/fetch-data {:id (:batch-id context)})
                      (get context data-source))
        post-url (if (:test context)
                   "http://test.crossref.org/servlet/deposit" 
                   "http://doi.crossref.org/servlet/deposit")
        operation (-> context :content-type type->deposit-operation)
        query-params {"operation" operation
                      "login_id" (:owner context)
                      "login_passwd" (:passwd context)}
        multipart  [{:name "fname"
                     :content data-object
                     :filename (str (:batch-id context) ".xml")}]
        params {:query-params query-params
                :timeout 20000
                :keepalive 30000
                :multipart multipart}
        {:keys [status headers body error]} @(hc/post post-url params)]
    (when (or (not= status 200) error) (throw (Exception.)))))

(declare perform-xml-deposit)

(defn perform-xml-deposit [context & with-delay]
  (let [delay-on-fail (deposit-data/begin-handoff! 
                       (:batch-id context)
                       :delay-fn expo-seconds-delay)]
    (doto (conf/get-service :executor)
      (.schedule
       (fn []
         (try
           (perform-xml-deposit-handoff context)
           (deposit-data/end-handoff! (:batch-id context))
           (catch Exception e
             (if (= delay-on-fail :never)
               (deposit-data/failed! (:batch-id context))
                 (perform-xml-deposit context delay-on-fail)))))
       (if with-delay (long (first with-delay)) 0)
       TimeUnit/MILLISECONDS)))
  context)
         
(defmethod deposit! "application/vnd.crossref.deposit+xml" [context]
  (-> context
      parse-xml
      parse-xml-deposit-dois
      alter-xml-batch-id
      alter-xml-email
      create-deposit
      perform-xml-deposit)
  (:batch-id context))

(defmethod deposit! "application/vnd.crossref.partial+xml" [context]
  (-> context
      parse-xml
      parse-xml-partial-deposit-dois
      alter-xml-batch-id
      alter-xml-email
      create-deposit
      perform-xml-deposit)
  (:batch-id context))
