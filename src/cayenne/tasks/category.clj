(ns cayenne.tasks.category
  (:use [cayenne.ids.issn :only [normalize-issn]]
        [cayenne.item-tree])
  (:require [somnium.congomongo :as m]
            [clojure.core.memoize :as memoize]
            [cayenne.conf :as conf]))

(defn get-category-name [category]
  (let [code (String/valueOf category)]
    (m/with-mongo (conf/get-service :mongo)
      (-> (m/fetch-one :categories :where {:code code}) (:name)))))

(defn get-issn-categories [issn]
  (let [norm-issn (normalize-issn issn)]
    (m/with-mongo (conf/get-service :mongo)
      (->> (m/fetch-one :issns :where {"$or" [{:p_issn norm-issn} {:e_issn norm-issn}]})
          (:categories)
          (map #(Integer/parseInt %))))))

(def get-category-name-memo (memoize/memo-lru get-category-name))

(def get-issn-categories-memo (memoize/memo-lru get-issn-categories))

(defn clear! []
  (memoize/memo-clear! get-category-name-memo)
  (memoize/memo-clear! get-issn-categories-memo))

(defn apply-to
  "Merge categories into an item if it is a journal item."
  ([item]
     (if (= :journal (get-item-subtype item))
       (do 
         (let [issns (map normalize-issn (get-item-ids item :issn))
               categories (set (mapcat get-issn-categories-memo issns))]
           (conf/log issns)
           (conf/log categories)
           (assoc item :category (map get-category-name-memo categories))))
       item))
  ([id item]
     [id (apply-to item)]))

