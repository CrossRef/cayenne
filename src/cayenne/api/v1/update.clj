(ns cayenne.api.v1.update
  (:require [clojure.data.json :as json]))

(defn parse-update
  "Parse a vector representing a change to the index. Change vectors
   are of the forms:

   [action subject-doi predicate value]
   [action [subject-doi subject-citation-id] predicate value]

   Where action is one of 'add', 'remove' or 'set'."
  [update-vec]
  (let [subject (second update-vec)]
    {:action (case (first update-vec)
               "set" :set
               "add" :add
               "remove" :remove
               (throw (Exception. "Unknown action")))
     :subject-doi (if (vector? subject)
                    (second subject)
                    subject)
     :subject-citation-id (when (vector? subject)
                            (second subject)) 
     :predicate (keyword (nth update-vec 2))
     :object (nth update-vec 3)}))

(defn read-updates-message
  "Read a JSON message representing a list of changes to the index."
  [rdr]
  (let [message-doc (-> rdr
                        (json/read :key-fn keyword))]
    (map parse-change (:message message-doc))))
  
  
