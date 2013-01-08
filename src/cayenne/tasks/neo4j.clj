(ns cayenne.tasks.neo4j
  (:require [clojurewerkz.neocons.rest :as neo]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.relationships :as nr]))

(defn- find-or-create [idx key value]
  (let [existing-node (nn/find-one idx key value)]
    (if (nil? existing-node)
      (let [new-node (nn/create {(symbol key) valye})]
        (nn/add-to-index (:id new-node) idx key value)
        new-node)
      existing-node)))

(defn- remove-all-rels [node]
  ())

(defn- add-citation-rels [node citations]
  ())  

(defn citations-neo4j-writer [server-path]
  (neo/connect! server-path)
  (nn/create-index "dois")
  (fn [doi-record]
    (-> (find-or-create "dois" "doi" (:doi doi-record))
        (remove-all-rels)
        (add-citation-rels (:citations doi-record)))))
    
