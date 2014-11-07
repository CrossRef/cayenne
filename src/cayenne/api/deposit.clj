(ns cayenne.api.deposit
  (:require [clojure.string :as string]
            [clojure.data.json :as json]
            [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi-id]
            [cayenne.tasks.patent :as patent]
            [cayenne.data.deposit :as deposit-data]
            [cayenne.data.work :as work]
            [cayenne.api.v1.query :as q]
            [clj-xpath.core :refer [$x $x:tag $x:text $x:text* $x:text+ $x:attrs 
                                    $x:attrs* $x:node
                                    xml->doc node->xml with-namespace-context
                                    xmlnsmap-from-root-node]]
            [org.httpkit.client :as hc])
  (:import [java.util UUID]
           [java.io StringWriter]
           [java.util.concurrent TimeUnit]
           [java.util.zip GZIPInputStream]))

;; handle deposit dispatch based on deposited content type

(defrecord DepositContext [owner passwd content-type object test pingback-url download-url batch-id])

(defn make-deposit-context [object content-type owner passwd test? pingback-url download-url]
  (DepositContext.
   owner
   passwd
   content-type
   object
   test?
   pingback-url
   download-url
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

(defn download-object [context]
  (if (:download-url context)
    (assoc context :object (-> context :download-url hc/get deref :body))
    context))

(defn create-deposit [context]
  (deposit-data/create!
   (if (:xml context)
     (-> context :xml node->xml (.getBytes "UTF-8"))
     (:object context))
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

(defn expo-seconds-delay-short [_ x]
  (if (> x 5)
    :never
    (* (Math/exp x) 1000)))

(defn no-repeat-delay [_ _] :never)

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

(defn perform-patent-citation-deposit [context separator]
  (let [batch-id (:batch-id context)
        test? (:test context)]
    (try
      (let [data-object (if (:deflate? context)
                          (GZIPInputStream.
                           (deposit-data/fetch-data {:id (:batch-id context)}))
                          (deposit-data/fetch-data {:id (:batch-id context)}))]
        (deposit-data/begin-handoff! batch-id)
        (with-open [rdr (clojure.java.io/reader data-object)]
          (patent/load-citation-csv rdr
                                    :consume (not test?) 
                                    :separator separator))
        (deposit-data/end-handoff! batch-id)
        (deposit-data/complete! batch-id))
      (catch Exception e
        (deposit-data/failed! batch-id :exception e)))))

(defn matched-citations [citations]
  (map
   #(let [match (-> {:request {:params {:query % :rows 1}}}
                    q/->query-context
                    work/fetch
                    (get-in [:message :items])
                    first)]
      {:text % :match match})
   citations))

(defn match-allow-token-count? [match]
  (> (count (string/split (:text match) #"\s+"))) 3)

(defn match-allow-score? [match]
  (>= (get-in match [:match :score]) 1))

(defn allowed-matches [matches]
  (map
   #(if (and (match-allow-score? %)
             (match-allow-token-count? %))
      %
      (dissoc % :match))
   matches))

(defn perform-pdf-citation-extraction [{batch-id :batch-id :as context} & with-delay]
  (let [delay-on-fail (deposit-data/begin-handoff!
                       batch-id :delay-fn expo-seconds-delay-short)]
    (doto (conf/get-service :executor)
      (.schedule
       (fn []
         (try
           (let [citations
                 (-> (conf/get-param [:upstream :pdf-service])
                     (hc/post {:headers {"Content-Type" "application/pdf"}
                               :query-params {:id batch-id}
                               :body (deposit-data/fetch-data {:id batch-id})})
                     deref
                     :body
                     json/read-str)]
             (deposit-data/set! batch-id
                                :citations
                                (-> citations matched-citations allowed-matches)))
           (deposit-data/end-handoff! batch-id)
           (deposit-data/complete! batch-id)
           (catch Exception e
             (if (= delay-on-fail :never)
               (deposit-data/failed! batch-id :exception e)
               (perform-pdf-citation-extraction context delay-on-fail)))))
       (if with-delay (long (first with-delay)) 0)
       TimeUnit/MILLISECONDS))
    context))

(defn deflate-object [context] (assoc context :deflate? true))
         
(defmethod deposit! "application/vnd.crossref.deposit+xml" [context]
  (-> context
      download-object
      parse-xml
      parse-xml-deposit-dois
      alter-xml-batch-id
      alter-xml-email
      create-deposit
      perform-xml-deposit)
  (:batch-id context))

(defmethod deposit! "application/vnd.crossref.partial+xml" [context]
  (-> context
      download-object
      parse-xml
      parse-xml-partial-deposit-dois
      alter-xml-batch-id
      alter-xml-email
      create-deposit
      perform-xml-deposit)
  (:batch-id context))

(defmethod deposit! "application/vnd.crossref.patent-citations+csv" [context]
  (-> context
      download-object
      create-deposit
      (perform-patent-citation-deposit \,))
  (:batch-id context))

(defmethod deposit! "application/vnd.crossref.patent-citations+csv+g-zip" [context]
  (-> context
      download-object
      create-deposit
      deflate-object
      (perform-patent-citation-deposit \,))
  (:batch-id context))

(defmethod deposit! "application/vnd.crossref.patent-citations+tab-separated-values" [context]
  (-> context
      download-object
      create-deposit
      (perform-patent-citation-deposit \tab))
  (:batch-id context))

(defmethod deposit! "application/vnd.crossref.patent-citations+tab-separated-values+g-zip" [context]
  (-> context
      download-object
      create-deposit
      deflate-object
      (perform-patent-citation-deposit \tab))
  (:batch-id context))

(defmethod deposit! "application/pdf" [context]
  (-> context
      download-object
      create-deposit
      perform-pdf-citation-extraction)
  (:batch-id context))
     
