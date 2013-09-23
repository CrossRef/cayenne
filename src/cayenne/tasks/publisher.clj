(ns cayenne.tasks.publisher
  (:require [cayenne.item-tree :as itree]
            [cayenne.conf :as conf]
            [cayenne.util :as util]
            [somnium.congomongo :as m]))

(defn ensure-publisher-indexes! [collection-name]
  (m/with-mongo (conf/get-service :mongo)
    (m/add-index! collection-name [:id])
    (m/add-index! collection-name [:tokens])
    (m/add-index! collection-name [:name])))

(defn insert-publisher
  "Locate any publisher information within an item tree
   and insert it into a mongo collection."
  [collection item]
  (let [publishers (itree/get-tree-rel item :publisher)]
    (doseq [publisher publishers]
      (let [id (first (itree/get-item-ids publisher))]
        (m/with-mongo (conf/get-service :mongo)
          (m/update! collection
                     {:id id}
                     {:id id
                      :tokens (util/tokenize-name (:name publisher))
                      :name (:name publisher)}
                     {:upsert true}))))))


