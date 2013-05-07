(ns cayenne.tasks.doaj
  (:use [cayenne.ids.issn :only [normalize-issn]]
        [cayenne.item-tree])
  (:require [somnium.congomongo :as m]
            [clojure.core.memoize :as memoize]
            [cayenne.conf :as conf]))

;; Right now we take DOAJ info from the old mongo collections.
;; At some point should be periodically downloading DOAJ info.

(defn get-oa-status [issn]
  (let [norm-issn (normalize-issn issn)
        oa-status (m/with-mongo (conf/get-service :mongo)
                    (-> :issns
                        (m/fetch-one :where {"$or" [{:p_issn norm-issn} {:e_issn norm-issn}]})
                        (:oa_status)))]
    (if (= oa-status "doaj")
      "Open Access"
      "Other")))

(def get-oa-status-memo (memoize/memo-lru get-oa-status))

(defn apply-to 
  ([item]
     (if (= (get-item-subtype item) :journal)
       (let [issn (first (map normalize-issn (get-item-ids item :issn)))]
         (assoc item :oa-status (get-oa-status-memo issn)))
       (assoc item :oa-status "Other")))
  ([id item]
     [id (apply-to item)]))

