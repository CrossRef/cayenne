(ns cayenne.tasks.neo4j
  (:use [cayenne.graft :only [insert-item ensure-indexes]]))

(defn record-neo-inserter []
  (ensure-indexes)
  (fn [records]
    (doseq [record records]
      (insert-item record))))
    
