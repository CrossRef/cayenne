(ns cayenne.tasks.category
  (:require [monger.collection :as coll]))

(def issn-categories (atom {}))
(def category-names (atom {}))

(defn load []
  (letfn [(to-category [record]
            [(Integer/parseInt (:code %)) (:name %)])
          (to-issns [record]
            (->> [:e_issn :p_issn]
                 ()))

    (coll/with-collection (conf/get-param [:coll :issns])
      (let [issns (->> (coll/find {}) (map to-issns) (flatten) (into {}))]
        (swap! cached-issns (constantly issns))))

    (coll/with-collection (conf/get-param [:coll :categories])
      (let [categories (->> (coll/find {}) (map to-category) (into {}))]
        (swap! cached-categories (constantly categories)))))))

(defn apply-to
  "Merge categories into an item if it is a journal item."
  [item]
  (if (and (= (:type item) :journal) (contains? item :id))
    (let [categories (->> (:id item)
                          (map #(cached-issns %))
                          (filter #(not (nil? %)))
                          (unique)
                          (flatten))]
      (merge item :categories categories))
    item))

