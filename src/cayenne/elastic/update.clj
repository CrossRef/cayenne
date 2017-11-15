(ns cayenne.elastic.update
  (:require [clj-time.core :as dt]
            [qbits.spandex :as elastic]
            [cayenne.conf :as conf]
            [cayenne.elastic.util :as elastic-util]))

(defn update-reference-count-command [subject-doi reference-count]
  [{:update {:_id subject-doi}}
   {:doc {:indexed (dt/now) :reference-count reference-count}}])

;; todo update script for reference.doi and reference.doi-asserted-by for
;; particular reference.key
(defn update-reference-doi-command [subject-doi reference-key object-doi]
  [{:update {:_id subject-doi}}
   {:doc {}}])

(defn index-updates [update-commands]
  (elastic/request
   (conf/get-service :elastic)
   {:method :post :url "/work/work/_bulk"
    :body (-> update-commands flatten elastic-util/raw-jsons)}))
