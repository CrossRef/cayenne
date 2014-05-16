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
  (m/add-index! collection-name [:names]))

(defn insert-publisher!
  "Upsert a publisher, combining multiple prefixes."
  [collection id prefix name location]
  (m/update! collection
             {:id id}
             {"$set" {:id id
                      :location (string/trim location)
                      :primary-name (string/trim name)}
              "$addToSet" {:prefixes prefix
                           :tokens {"$each" (util/tokenize-name name)}
                           :names (string/trim name)}}))

(defn load-publishers [collection]
  (m/with-mongo (conf/get-service :mongo)
    (ensure-publisher-indexes! collection)
    (doseq [prefix-number (range 1000 100000)]
      (let [prefix (str "10." prefix-number)
            url (str
                 (conf/get-param [:upstream :prefix-info-url])
                 prefix)
            resp (http/get url {:connection-manager (conf/get-service :conn-mgr)
                                :throw-exceptions false
                                :as :byte-array})
            root (when (= 200 (:status resp))
                   (-> (:body resp) io/reader xml/parse zip/xml-zip))]
        (when root
          (when-let [id (zx/xml1-> root :publisher :member_id)]
            (insert-publisher!
             collection
             (-> id zx/text (Integer/parseInt))
             prefix
             (zx/text (zx/xml1-> root :publisher :publisher_name))
             (zx/text (zx/xml1-> root :publisher :publisher_location)))))))))
 
