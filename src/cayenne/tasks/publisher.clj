(ns cayenne.tasks.publisher
  (:import [java.net URI]
           [java.io FileNotFoundException])
  (:require [cayenne.item-tree :as itree]
            [cayenne.conf :as conf]
            [cayenne.util :as util]
            [cayenne.data.work :as works]
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
            [somnium.congomongo :as m]))

(defn ensure-publisher-indexes! [collection-name]
  (m/add-index! collection-name [:id])
  (m/add-index! collection-name [:tokens])
  (m/add-index! collection-name [:prefixes])
  (m/add-index! collection-name [:names])
  (m/add-index! collection-name [:public-references]))

(defn update-publisher!
  "Upsert a publisher, combining multiple prefixes."
  [collection id prefixes name location prefix-names prefix-infos]
  (m/update! collection
             {:id id}
             {"$set" {:id id
                      :location (string/trim location)
                      :primary-name (string/trim name)
                      :prefixes prefixes
                      :tokens (util/tokenize-name name)
                      :prefix prefix-infos
                      :public-references (not (nil?
                                               (some true?
                                                     (map :public-references prefix-infos))))
                      :names (set (conj (map string/trim prefix-names)
                                        (string/trim name)))}}))

(defn get-member-list []
  (let [url (str (conf/get-param [:upstream :prefix-info-url]) "all")
        response (http/get url
                           {:connection-manager (conf/get-service :conn-mgr)
                            :throw-exceptions false
                            :as :string})]
    (when (= 200 (:status response))
      (-> response :body (json/read-str :key-fn keyword)))))

(defn get-prefix-info [prefix]
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
       :name (zx/text (zx/xml1-> root :publisher :prefix_name))
       :location (zx/text (zx/xml1-> root :publisher :publisher_location))
       :public-references (= "true" (zx/text (zx/xml1-> root :publisher :allows_public_access_to_refs)))})))

(defn load-publishers [collection]
  (m/with-mongo (conf/get-service :mongo)
    (ensure-publisher-indexes! collection)
    (doseq [member (get-member-list)]
      (let [prefixes (filter (complement string/blank?) (:prefixes member))
            prefix-infos (map get-prefix-info prefixes)
            prefix-names (map :name prefix-infos)
            publisher-location (first (filter (complement nil?)
                                              (map :location prefix-infos)))]
        (update-publisher!
         collection
         (:memberId member)
         prefixes
         (:name member)
         publisher-location
         prefix-names
         (map #(dissoc % :location) prefix-infos))))))
