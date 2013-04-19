(ns cayenne.tasks.neo4j
  (:use [cayenne.graft :only [with-transaction insert-item]]))

(defn record-neo-inserter []
  (fn [records]
    (with-transaction
      (doseq [record records]
        (insert-item record)))))
    
