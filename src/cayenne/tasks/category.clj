(ns cayenne.tasks.category
  (:use [cayenne.ids.issn :only [normalize-issn]])
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

(def get-issn-categories-memo (memoize/memo-lru get-issn-categories))

;; (defn apply-to
;;   "Merge categories into an item if it is a journal item."
;;   [item]
;;   (if (and (= (:type item) :journal) (contains? item :id))
;;     (let [categories (->> (:id item)
;;                           (map #(cached-issns %))
;;                           (filter #(not (nil? %)))
;;                           (unique)
;;                           (flatten))]
;;       (merge item :categories categories))
;;     item))

