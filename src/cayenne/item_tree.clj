(ns cayenne.item-tree
  (:require [cayenne.ids :as ids]))

(def contributor-rels [:author :chair :translator :editor :contributor])

(defn add-property [item k v]
  (assoc item k v))

(defn add-properties [item m]
  (merge item m))

(defn add-id [item id]
  (let [existing-ids (or (:id item) [])]
    (assoc item :id (conj existing-ids id))))

(defn add-relation [item rel-type rel-item]
  (let [existing-items (or (get-in item [:rel rel-type]) [])]
    (assoc-in item [:rel rel-type] (conj existing-items rel-item))))

(defn add-relations [item rel-type rel-items]
  (let [existing-items (or (get-in item [:rel rel-type]) [])]
    (assoc-in item [:rel rel-type] (concat existing-items rel-items))))

(defn make-item 
  ([type subtype]
     {:type type :subtype subtype})
  ([type]
     {:type type}))

(defn in? 
  "true if seq contains elm"
  [seq elm]  
  (some #(= elm %) seq))

(defn children [item-tree]
  (flatten (vals (:rel item-tree))))

(defn item-seq
  [item]
  (flatten (tree-seq (constantly true) children item)))

(defn find-items [item-tree match-fn]
  (filter match-fn (item-seq item-tree)))

(defn find-item [item-tree match-fn]
  (first (find-items item-tree match-fn)))

(defn find-item-of-subtype [item-tree subtype]
  (find-item item-tree #(= (:subtype %) subtype)))

(defn find-item-of-type [item-tree type]
  (find-item item-tree #(= (:type %) type)))

(defn find-item-with-id [item-tree id]
  (find-item item-tree #(in? (:id %) id)))

(defn find-item-with-id-type [item-tree id-type]
  (find-item item-tree #(in? (map ids/id-uri-type (:id %)) id-type)))

(defn get-item-ids [item-tree & more]
  (let [id-type (first more)]
    (if id-type
      (filter #(= (ids/id-uri-type %) id-type) (:id item-tree))
      (:id item-tree))))

(defn get-tree-ids [item-tree & more]
  (let [id-type (first more)]
    (if id-type
      (filter 
       #(= (ids/id-uri-type %) id-type)
       (mapcat :id (item-seq item-tree)))
      (mapcat :id (item-seq item-tree)))))

(defn get-item-rel [item-tree rel-type]
  (get-in item-tree [:rel rel-type])) 

(defn get-tree-rel
  "Return all items that are the endpoint of a rel of type rel-type,
   regardless of where the relation starts in the tree."
  [item-tree rel-type]
  (if-let [related-items (get-in item-tree [:rel rel-type])]
    (concat 
     related-items 
     (flatten 
      (map #(get-tree-rel % rel-type) (children item-tree))))
    (flatten 
     (map #(get-tree-rel % rel-type) (children item-tree)))))

(defn get-item-type [item-tree]
  (:type item-tree))

(defn get-item-subtype [item-tree]
  (:subtype item-tree))

;; todo should instead remove all rels to ancestors
(defn without-id
  "Returns an item tree without items with id."
  [item-tree id]
  item-tree)

(defn path-to* [item-tree path id]
  (if (in? (:id item-tree) id)
    path
    (first 
     (filter
      (complement nil?)
      (map 
       #(path-to* % (conj (vec path) item-tree) id)
       (children item-tree))))))

(defn path-to 
  "Get a seq of all the ancestor items of the first found item with id."
  [item-tree id]
  (path-to* item-tree '() id))
       
(defn flatten-ancestors
  "Flatten ancestors of an item in item-tree with id. Ancestors of item are
   placed as embeded maps with the parent item type as key."
  [item-tree id]
  (let [around-item (find-item-with-id item-tree id)]
    (reduce
     #(assoc %1 (:subtype %2) (without-id %2 id))
     around-item
     (path-to item-tree id))))

(defn centre-on
  "Flatten ancestors of an item in item-tree with id. Ancestors of item are
   placed as embeded maps with the parent item type as key."
  [item-tree id]
  (let [around-item (find-item-with-id item-tree id)]
    (reduce
     #(add-relation %1 :ancestor (without-id %2 id))
     around-item
     (path-to item-tree id))))


   
; defn issns dois etc
