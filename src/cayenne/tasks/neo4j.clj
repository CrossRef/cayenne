(ns cayenne.tasks.neo4j
  (:use cayenne.item-tree)
  (:require [cayenne.conf :as conf]
            [cayenne.ids :as ids]
            [clojure.math.combinatorics :as c]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.relationships :as nrl]))

;; todo for now we are ignoring supplementary ids. can be reintroduced once
;; they are namespaced by owner prefix

;; todo award numbers as uris

;; todo better handling of isbn uris

(defn relation-triples [relation from to]
  (for [from-id (get-item-ids from) to-id (get-item-ids to)
        :when (and (not= :supplementary (ids/id-uri-type from-id))
                   (not= :supplementary (ids/id-uri-type to-id)))]
    [from-id relation to-id]))

(defn as-relation-triples [item-tree]
  (->> (for [item (item-seq item-tree)
             [rel children] (similar-rel-children item)
             child children
             :when (and (has-id? item) (has-id? child))]
         (relation-triples rel item child))
       (flatten)
       (partition 3)))

(defn as-same-as-triples [item-tree]
  (for [item (item-seq item-tree)
        [left right] (c/combinations (:id item) 2)
        :when (and (> (count (:id item)) 1)
                   (not= :supplementary (ids/id-uri-type left))
                   (not= :supplementary (ids/id-uri-type right)))]
    [left :same-as right]))

(defn as-triples [item-tree]
  (concat
   (as-relation-triples item-tree)
   (as-same-as-triples item-tree)))

(defn create-indexes []
  (nn/create-index "items"))

(defn insert-item [item]
  (doseq [triple (as-triples item)]
    (conf/log (vec triple))
    (let [left-id (first triple)
          right-id (last triple)
          relation (second triple)
          left (or (nn/find-one "items" "id" left-id) (nn/create {:uri left-id}))
          right (or (nn/find-one "items" "id" right-id) (nn/create {:uri right-id}))]
      (nn/add-to-index left "items" "id" left-id)
      (nn/add-to-index right "items" "id" right-id)
      (when-not (nrl/first-outgoing-between left right [relation])
        (nrl/create left right relation)))))

