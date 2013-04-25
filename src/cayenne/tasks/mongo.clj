(ns cayenne.tasks.mongo
   (:require [monger.core :as mg])
   (:require [monger.collection :as mc]
             [cayenne.ids :as ids]))

(defn citations-mongo-writer []
  (mg/connect!)
  (fn [item]
    (when (and (:doi item) (:citations item))
      (mc/insert "citations" 
                 {:doi (:doi item)
                  :citations (:citations item)}))))

(defn to-mongo-insertable [item]
  (dissoc item :rel))

(defn insert-work [item parents parent-rel]
  (condp = (:subtype item)
    :journal (-> item (to-mongo-insertable))
    :journal-issue (-> item (to-mongo-insertable) (merge-ancestors :journal))
    :journal-volume (-> item (to-mongo-insertable (merge-ancestors :journal-issue :journal)
      
  (-> item
      (to-mongo-insertable)
      (merge-descendents)
      (merge-ancestors)

(defn insert-citation [item parents parent-rel]
  (-> item
      (to-mongo-insertable)))

(defn insert-person [item parents parent-rel]
  (-> item
      (to-mongo-insertable)
      (merge-descendents)))

(defn insert-generic-item [item parents parent-rel]
  (-> item
      (to-mongo-insertable)))

(defn insert-item [item parents parent-rel]
  (when (contains? item :id)
    (cond (= (:type item) :work)
          (insert-work item parents parent-rel)
          (= (:type item) :citation)
          (insert-work item parents parent-rel)
          (= (:type item) :person)
          (insert-work item parents parent-rel)
          :else
          (insert-generic-item item parents parent-rel)))
  (doseq [rel-type (keys (:rel item))]
    (doseq [child-item (get-in item [:rel rel-type])]
      (insert-item child-item (conj parents item) rel-type))))

(defn item-mongo-inserter []
  (fn [items]
    (doseq [item items]
      (insert-item item [] nil))))
      (when (contains? item :id)
        (insert-item item))





;;;;

(defn find-item [for-id]
  ())

(defn insert-item 
  "Insert item given an item tree. Will insert a record based around the
   position in the tree of the for-id."
  [item-tree for-id]
  (let [item-context (find-item for-id)
        doc (-> (to-mongo-insertable (:item item-context))
                (merge-descendents (:item item-context))
                (merge-ancestors (:ancestors item-context)))]
    (coll/insert (conf/get-param [:coll :items]) doc)))
    
