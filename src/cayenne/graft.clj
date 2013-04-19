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

(def ^:dynamic *graph-db* nil)
(def ^:dynamic *graph-server* nil)

(defn make-embedded-graph-db
  [path]
  (EmbeddedGraphDatabase. path))

;(defn make-graph-server
;  [db]
;  (WrappingNeoServerBootstrapper *graph-db*))

(defn set-graph-db!
  [db]
  (alter-var-root #'*graph-db* (constantly db)))

(defn set-graph-server!
  [server]
  (alter-var-root #'*graph-server* (constantly server)))

(defmacro with-connection [db & body]
  `(binding [*graph-db* ~db]
     ~@body))

(defmacro with-transaction
  [& body]
  `(let [transaction# (.beginTx *graph-db*)]
     (try
       (let [val# (do ~@body)]
         (.success transaction#)
         val#)
       (finally (.finish transaction#)))))

(defmacro with-transaction*
  [db & body]
  `(with-connection ~db
     (with-transaction
       ~@body)))

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
      (.. *graph-db* (index) (forNodes item-type) (get "id" item-id)))))

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
  (recreate-node (.createNode *graph-db*) properties))

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

;(set-graph-db! (make-embedded-graph-db (conf/get-param [:db :neo4j :dir])))
;(set-graph-server! (make-graph-server *graph-db*))
