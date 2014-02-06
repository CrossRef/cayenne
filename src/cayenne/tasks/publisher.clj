(ns cayenne.tasks.publisher
  (:import [java.net URI]
           [java.io FileNotFoundException])
  (:require [cayenne.item-tree :as itree]
            [cayenne.conf :as conf]
            [cayenne.util :as util]
            [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]
            [somnium.congomongo :as m]))

(defn ensure-publisher-indexes! [collection-name]
  (m/with-mongo (conf/get-service :mongo)
    (m/add-index! collection-name [:id])
    (m/add-index! collection-name [:tokens])
    (m/add-index! collection-name [:prefixes])
    (m/add-index! collection-name [:names])))

(defn insert-publisher! 
  "Upsert a publisher, combining multiple prefixes."
  [collection id prefix name location]
  (m/with-mongo (conf/get-service :mongo)
    (m/update! collection
               {:id id}
               {"$set" {:id id
                        :location (string/trim location)}
                "$addToSet" {:prefixes prefix
                             :tokens {"$each" (util/tokenize-name name)}
                             :names name}})))

(defn load-publishers [collection]
  (ensure-publisher-indexes! collection)
  (doseq [prefix-number (range 1000 100000)]
    (let [prefix (str "10." prefix-number)
          url (str 
               (conf/get-param [:upstream :prefix-info-url])
               prefix)
          root (try
                 (-> url io/reader xml/parse zip/xml-zip)
                 (catch Exception e nil))]
      (when root
        (when-let [id (zx/xml1-> root :publisher :member_id)]
          (insert-publisher!
           collection
           (-> id zx/text (Integer/parseInt))
           prefix
           (zx/text (zx/xml1-> root :publisher :publisher_name))
           (zx/text (zx/xml1-> root :publisher :publisher_location))))))))
