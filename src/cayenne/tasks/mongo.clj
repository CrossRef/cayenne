(ns cayenne.tasks.mongo
   (:require [monger.core :as mg])
   (:require [monger.collection :as mc]))

(defn citations-mongo-writer []
  (mg/connect!)
  (fn [item]
    (when (and (:doi item) (:citations item))
      (mc/insert "citations" 
                 {:doi (:doi item)
                  :citations (:citations item)}))))

