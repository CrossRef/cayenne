(ns cayenne.tasks.mongo
  (:require [cayenne.conf :as conf]
            [cayenne.item-tree :as itree]
            [cayenne.ids :as ids]
            [cayenne.ids.doi :as doi-id]
            [clj-time.core :as dt]
            [somnium.congomongo :as m]))

;; insert IDed items into a mongo collection.

(defn ids-without-supp [item]
  (let [ids (itree/get-item-ids item)]
    (filter #(not= :supplementary (ids/id-uri-type %)) ids)))
        
(defn insert-item 
  "Insert an item for all its IDs except supplementary IDs, which may
   not be unique across all items. An item record that already exists
   with some or all of the item's IDs will be replaced."
  [collection item]
  (let [ids (ids-without-supp item)]
    (m/with-mongo (conf/get-service :mongo)
      (m/update! collection
                 {:id {"$in" ids}}
                 {:id ids
                  :rindex (rand)
                  :updated (java.util.Date.)
                  :item item}
                 :upsert true))))

(defn check-for-item [collection item-id]
  (m/with-mongo (conf/get-service :mongo)
    (m/fetch-one collection :where {:id item-id})))

(defn check-for-dois [collection dois]
  (-> dois
      (map doi-id/to-long-doi-uri)
      (map (partial check-for-item collection))
      (filter nil?)))
  
