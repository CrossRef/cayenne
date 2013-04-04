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
(defmethod find-item-node :id [item] (match-exact item :subtype :value))
(defmethod find-item-node :org [item] (match-exact item :name))
;(defmethod find-item-node :url [item] (match-exact item :value))
;(defmethod find-item-node :title [item] (match-exact item :subtype :language :value))
;(defmethod find-item-node :date [item] (match-exact item :day :month :year :time-of-year))
(defmethod find-item-node :title [item] nil)
(defmethod find-item-node :date [item] nil)
(defmethod find-item-node :url [item] nil)

(def index-for-type {:work [:subtype]
                     :org [:name] 
                     :id [:subtype :value] 
                     :url [:value]
                     :title [:subtype :language :value]
                     :date [:day :month :year :time-of-year]})

(defn ensure-indexes []
  (neo/connect! "http://localhost:7474/db/data/")
  (doseq [index-name (keys index-for-type)] (nodes/create-index index-name)))

(defn without-nils [record]
  (reduce (fn [m [k v]] (if (nil? v) (dissoc m k) m)) record record))

(defn index-item-node
  "Fields are concatenated then indexed under an index whose name is the 
   concatenation of field names."
  [item node]
  (let [index-fields (get index-for-type (:type item))]
    (doseq [index-field (get index-for-type (:type item))]
      (when-let [value (get item index-field)]
        (nodes/add-to-index (:id node) (name (:type item)) (name index-field) value))))
  node)

(defn make-item-node [item]
  (let [doc (without-nils (dissoc item :rel))
        node (nodes/create doc)]
    (index-item-node item node)))

;; todo delete rel out of node and everything connected.
(defn item-node [item]
  (let [node (or (find-item-node item) (make-item-node item))]
    ;(when (= (:type node) :id)
    ;  ())
    node))

(defn insert-item-with-rel [item rel from-node]
  (let [parent-node (item-node item)]
    (rels/create from-node parent-node (name rel))
    (doseq [rel-type (keys (:rel item))]
      (doseq [child-item (get-in item [:rel rel-type])]
        (insert-item-with-rel child-item rel-type parent-node)))))

(defn insert-item
  "Insert a metadata item into neo4j."
  [item]
  (let [parent-node (item-node item)]
    (doseq [rel-type (keys (:rel item))]
      (doseq [child-item (get-in item [:rel rel-type])]
        (insert-item-with-rel child-item rel-type parent-node)))))

(defn match-exact [item & fields]
  (let [index-fields (index-for-type (:type item))
        field-fn #(str (name %) ":\"" (name (get item %)) "\"")
        query (string/join " AND " (map field-fn index-fields))]
    (first (nodes/query (name (:type item)) query))))

;; todo afterwards, run dedup tasks. For example, for each journal work, merge similar journal
;; volumes it is pointing to. So on.
