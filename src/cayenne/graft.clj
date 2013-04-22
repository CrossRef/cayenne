(ns cayenne.graft
  (:import [org.neo4j.graphdb Direction NotFoundException DynamicRelationshipType])
  (:use [cayenne.util :only [with-safe-vals]])
  (:require [cayenne.conf :as conf]
            [clojure.string :as string]))

(defmacro with-transaction
  [& body]
  `(let [transaction# (.beginTx (conf/get-service :neo4j-db))]
     (try
       (let [val# (do ~@body)]
         (.success transaction#)
         val#)
       (finally (.finish transaction#)))))

(defn find-node-for-item 
  "Find a node that matches the type and an ID from item."
  [item]
  (let [item-type (:type item)
        item-id (first (:id item))]
    (when (and item-id item-type)
      (.. (conf/get-service :neo4j-db) 
          (index) 
          (forNodes (name item-type))
          (get "id" item-id)
          (getSingle)))))

(defn remove-ambiguous-at
  "Traverses nodes from a point, stopping when encountering nodes with the property
   id, and deleting any nodes without it. All relations connecting to nodes found
   without an id property are also deleted."
  [node]
  (if-not (.hasProperty node "id")
    (do
      (doseq [rel (.getRelationships node Direction/OUTGOING)]
        (remove-ambiguous-at (.getEndNode rel))
        (.delete rel))
      (doseq [rel (.getRelationships node Direction/INCOMING)]
        (remove-ambiguous-at (.getStartNode rel))
        (.delete rel))
      (.delete node))))

(defn remove-ambiguous-from
  "Same as remove-ambiguous-at but starts from all nodes connected to the given
   node. The given node is not deleted, nor checked for ambiguouity."
  [node]
  (doseq [rel (.getRelationships node Direction/OUTGOING)]
    (remove-ambiguous-at (.getEndNode rel)))
  (doseq [rel (.getRelationships node Direction/INCOMING)]
    (remove-ambiguous-at (.getStartNode rel))))

;; todo instead of calling find-node-for-item all over the place, iterate
;; once over the item and attach nodes to the item tree where they exist

(defn remove-ambiguous-for-item [item]
  (when (:id item) 
    (when-let [node (find-node-for-item item)]
      (remove-ambiguous-from node)))
  (doseq [[rel-type children] (:rel item)]
    (doseq [child children]
      (remove-ambiguous-for-item child))))

(defn index-node [node type key val]
  (.. (conf/get-service :neo4j-db) (index) (forNodes (name type)) (add node (name key) val)))

(defn update-node [node properties]
  (doseq [[k v] (with-safe-vals properties)]
    (.setProperty node (name k) v))
  (when (:id properties)
    (index-node node (:type properties) "id" (:id properties)))
  node)

(defn recreate-node [node properties]
  (doseq [k (.getPropertyKeys node)]
    (.removeProperty node (name k)))
  (update-node node properties)
  node)

(defn create-node []
  (.createNode (conf/get-service :neo4j-db)))

(defn link-nodes [from type to]
  (.createRelationshipTo from to (DynamicRelationshipType/withName (name type))))

(defn insert-item* 
  "Insert a child item, creating a relation from its parent."
  [item parent rel-type]
  (let [node (or (find-node-for-item item) (create-node))]
    (recreate-node node (dissoc item :rel))
    (link-nodes parent rel-type node)
    (doseq [[rel-type children] (:rel item)]
      (doseq [child children] 
        (insert-item* child node rel-type)))))

(defn insert-item 
  "Insert an item into the graph db. First removes any ambiguous nodes
   reachable from non-ambiguous items in the item's tree."
  [item]
  (remove-ambiguous-for-item item)
  (let [node (or (find-node-for-item item) (create-node))]
    (recreate-node node (dissoc item :rel))
    (doseq [[rel-type children] (:rel item)]
      (doseq [child children] 
        (insert-item* child node rel-type)))))

