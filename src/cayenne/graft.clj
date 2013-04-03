(ns cayenne.graft
  (:require [clojure.string :as string]
            [clojurewerkz.neocons.rest :as neo]
            [clojurewerkz.neocons.rest.nodes :as nodes]
            [clojurewerkz.neocons.rest.relationships :as rels]))

;; Graft metadata trees into neo4j. Each item type has a strategy to find an existing
;; matching node in the graph. Some item types never match existing nodes and are
;; always created new (they are ambiguous types).

(declare match-exact)

(defmulti find-item-node :type)

(defmethod find-item-node :work [item] nil)
(defmethod find-item-node :person [item] nil)
(defmethod find-item-node :citation [item] nil)
(defmethod find-item-node :event [item] nil)
(defmethod find-item-node :org [item] (match-exact item :name))
(defmethod find-item-node :id [item] (match-exact item :subtype :value))
(defmethod find-item-node :title [item] (match-exact item :subtype :language :value))

(def index-for-type {:org [:name] :id [:subtype :value] :title [:subtype :language :value]})

(defn as-index-key [v] (string/join "_" v))
(defn as-index-val [v] (string/join "_" v))

(defn index-item-node
  "Fields are concatenated then indexed under an index whose name is the 
   concatenation of field names."
  [item node]
  (let [index-fields (get index-for-type (:type item))]
    (nodes/add-to-index (:type item) 
                        (as-index-key index-fields)
                        (as-index-val (map item index-fields))
                        node))) 

(defn make-item-node [item]
  (let [node (nodes/add-node )]
    (index-item-node item node)))

(defn item-node [item]
  (or (find-item-node item) (make-item-node item)))

;; todo make rel to children

(defn insert-item
  "Insert a metadata item into neo4j."
  [item]
  (neo/connect!)
  (let [parent-node (item-node item)]
    (doseq [rel-type (keys (:rel item))]
      (doseq [child-item (get-in item [:rel rel-type])]
        (insert-item child-item rel-type parent-node))))

  [item rel to-node]
  (neo/connect!)
  (let [parent-node (item-node item)]
    (rel/add-rel to-node rel parent-node)
    (doseq [rel-type (keys (:rel item))]
      (doseq [child-item (get-in item [:rel rel-type])]
        (insert-item child-item rel-type parent-node)))))

(defn match-exact [item & fields]
  (nodes/find-one (:type item)
                    (as-index-key fields)
                    (as-index-val (map item fields))))

