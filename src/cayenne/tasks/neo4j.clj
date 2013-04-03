(ns cayenne.tasks.neo4j
  (:use [cayenne.graft :only [insert-item]]))

(defn record-neo-inserter []
  (fn [records]
    (doseq [record records]
      (insert-item record))))
    
