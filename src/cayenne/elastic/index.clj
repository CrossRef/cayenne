(ns cayenne.elastic.index
  (:require [cayenne.elastic.convert :as convert]
            [qbits.spandex :as elastic]
            [cayenne.conf :as conf]
            [cayenne.elastic.util :as elastic-util]))

(defn index-command [item]
  (let [es-doc (convert/item->es-doc item)]
    [{:index {:_id (:doi es-doc)}} es-doc]))

(defn index-item [item]
  (elastic/request
   (conf/get-service :elastic)
   {:method :post :url "/work/work/_bulk"
    :body (elastic-util/raw-jsons (index-command item))}))

(defn index-items [items]
  (elastic/request
   (conf/get-service :elastic)
   {:method :post :url "/work/work/_bulk"
    :body (elastic-util/raw-jsons (mapcat index-command items))}))
