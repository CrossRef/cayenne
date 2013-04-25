(ns cayenne.graft
  (:import [org.neo4j.graphdb Direction NotFoundException DynamicRelationshipType])
  (:require [cayenne.neo :as neo])
  (:require [cayenne.conf :as conf]
            [clojure.string :as string]))

(defn ambiguous-item? [item] (not (:id item)))
(defn ambiguous-node? [node] (not (has-property item :id)))
(defn item->properties [item] (neo/as-properties (dissoc item :rel)))
(defn node-for-item [item] (find-node) (create-node))

(defrecord GraftContext [item item-tree parent-node parent-item parent-rel])

(defn graft-by 
  "Graft an item into a node. First look for nodes with matching id,
   if none found try the finders (based on looking around from a parent,)
   if none found create a new node."
  [graft-context & {:keys [match-properties match-title only-rel-type]
                    :or {match-properties [] match-title false only-rel-type :any}}]
  ; first look for any ids already in the graph.

  ; if not, try match-properties and only-rel-type (search from parent)

  ; if not, create a new node

  ; update node properties
  ())

(defmulti graft-item* (fn [item] [(:type item) (:subtype item)])

(defmethod graft-item* [:work :journal] [graft-context]
  (graft-by graft-context))

(defmethod graft-item* [:work :journal-issue] [issue parent-node]
  (graft-by graft-context :try-id true :matching-properties [:subtype :issue]))

(defmethod graft-item* [:work :journal-volume] [volume parent-node]
  (graft-by graft-context :id-match :yes :property-match [:subtype :volume]))

(defmethod graft-item* [:work :journal-article] [article parent-node]
  (graft-by graft-context :id-match :yes))

(defmethod graft-item* [:org nil] [org parent-node]
  (graft-by graft-context :id-match :yes :property-match [:type :name]))

(defmethod graft-item* [:date nil] [date parent-node]
  (graft-by graft-context :property-match [:type :day :year :month :time-of-year]))

(defmethod graft-item* [:person nil] [person parent-node]
  (graft-by graft-context :id-match :yes))

;; todo title subtypes should be relationship types!

(defmethod graft-item* [:title :short] [title parent-node]
  (graft-by :property-match [:type :subtype :language :value]
            title parent-node))

(defmethod graft-item* [:title :long] [title parent-node]
  (graft-by :property-match [:type :subtype :language :value]
            title parent-node))

(defmethod graft-item* [:title :secondary] [title parent-node]
  (graft-by :property-match [:type :subtype :language :value]
            title parent-node))

(defn graft-item [item]
  (graft-item* (GraftContext. item 
  (graft-item* item nil))



(defmulti graft-rels first :default :other)

(defmethod graft-rels :author [authors parent-item parent-node]
  (let [out-rels (out-rels-from parent-node :author)]
    (delete (end-nodes out-rels) :when ambiguous-node?)
    (delete out-rels)
    (doseq [author authors]
      (-> (node-for-item author)
          (update-node (item->properties author))
          (create-rel parent-node :author)))))



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
        (.delete rel)
        (remove-ambiguous-at (.getEndNode rel)))
      (doseq [rel (.getRelationships node Direction/INCOMING)]
        (.delete rel)
        (remove-ambiguous-at (.getStartNode rel)))
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
    (index-node node (name (:type properties)) "id" (into-array (:id properties))))
  node)

(defn recreate-node [node properties]
  (doseq [k (.getPropertyKeys node)]
    (.removeProperty node (name k)))
  (update-node node properties)
  node)

(defn create-node []
  (.createNode (conf/get-service :neo4j-db)))

(defn linked? [from type to]
  (->> (.getRelationships from (DynamicRelationshipType/withName (name type)) Direction/OUTGOING) 
       (filter #(= (.getId to) (.getId (.getEndNode %))))
       (count) 
       (zero?) 
       (not)))

(defn link-nodes [from type to]
  (when (not (linked? from type to))
    (.createRelationshipTo from to (DynamicRelationshipType/withName (name type)))))

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

