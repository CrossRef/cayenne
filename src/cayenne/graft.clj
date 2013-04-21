(ns cayenne.graft
  (:import ;[org.neo4j.server WrappingNeoServerBootstrapper]
           [org.neo4j.kernel EmbeddedGraphDatabase]
           [org.neo4j.graphdb Direction NotFoundException RelationshipType])
           ;[org.neo4j.cypher.javacompat ExecutionEngine CypherParser])
  (:require [cayenne.conf :as conf]
            [clojure.string :as string]
            [clojurewerkz.neocons.rest :as neo]
            [clojurewerkz.neocons.rest.nodes :as nodes]
            [clojurewerkz.neocons.rest.relationships :as rels]))

(defmacro with-transaction
  [& body]
  `(let [transaction# (.beginTx (conf/get-service :neo4j-db))]
     (try
       (let [val# (do ~@body)]
         (.success transaction#)
         val#)
       (finally (.finish transaction#)))))

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

(defn find-node-for-item 
  "Find a node that matches the type and an ID from item."
  [item]
  (let [item-type (:type item)
        item-id (first (:id item))]
    (when (and item-id item-type)
      (.. (conf/get-service :neo4j-db) (index) (forNodes item-type) (get "id" item-id)))))

(defn update-node [node properties]
  (doseq [[k v] properties]
    (.setProperty node (name k) v))
  node)

(defn recreate-node [node properties]
  (doseq [k (.getPropertyKeys node)]
    (.removeProperty node k))
  (update-node node properties)
  node)

(defn create-node [properties]
  (recreate-node (.createNode (conf/get-service :neo4j-db) properties)))

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

(defn insert-item [item]
  ())

