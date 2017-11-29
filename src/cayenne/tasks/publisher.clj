(ns cayenne.tasks.publisher
  (:import [java.net URI]
           [java.io FileNotFoundException])
  (:require [cayenne.item-tree :as itree]
            [cayenne.conf :as conf]
            [cayenne.util :as util]
            [cayenne.data.work :as works]
            [cayenne.elastic.util :as elastic-util]
            [cayenne.util :refer [?> ?>>]]
            [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [clojure.data.zip.xml :as zx]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clj-time.core :as dt]
            [clj-time.format :as df]
            [clj-time.coerce :as dc]
            [clj-http.client :as http]
            [qbits.spandex :as elastic]))

(defn get-member-list
  "Get a list of members from the Crossref prefix information API."
  []
  (let [url (str (conf/get-param [:upstream :prefix-info-url]) "all")
        response (http/get url
                           {:connection-manager (conf/get-service :conn-mgr)
                            :throw-exceptions false
                            :as :string})]
    (when (= 200 (:status response))
      (-> response :body (json/read-str :key-fn keyword)))))

(defn get-prefix-info
  "Return information about an owner prefix from the Crossref prefix information
   API."
  [member-id prefix]
  (let [url (str
             (conf/get-param [:upstream :prefix-info-url])
             prefix)
        response (http/get url {:connection-manager (conf/get-service :conn-mgr)
                                :throw-exceptions false
                                :as :byte-array})
        root (when (= 200 (:status response))
               (-> (:body response) io/reader xml/parse zip/xml-zip))]
    (when root
      {:value prefix
       :member-id member-id
       :name (zx/text (zx/xml1-> root :publisher :prefix_name))
       :location (zx/text (zx/xml1-> root :publisher :publisher_location))
       :reference-visibility
       (if-let [ref-loc (zx/xml1-> root
                                   :publisher
                                   :references_distribution)]
         (-> ref-loc zx/text keyword)
         :closed)})))

(defn index-command
  "Turn a member record from the Crossref prefix information API into
   a command, document pair that will index the member in ES."
  [member]
  (let [member-id (:memberId member)
        prefixes (filter (complement string/blank?) (:prefixes member))
        prefixes (map (partial get-prefix-info member-id) prefixes)
        publisher-location (first (filter (complement nil?)
                                              (map :location prefixes)))]
    [{:index {:_id member-id}}
     {:id member-id
      :primary-name (:name member)
      :location publisher-location
      :token (util/tokenize-name (:name member))
      :prefix prefixes}]))

(defn index-members
  "Index members into ES."
  []
  (doseq [some-members (partition-all 100 (get-member-list))]
    (let [bulk-body (->> some-members
                         (map index-command)
                         flatten)]
      (elastic/request
       (conf/get-service :elastic)
       {:method :post
        :url "member/member/_bulk"
        :body (elastic-util/raw-jsons bulk-body)}))))
     
