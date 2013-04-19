(ns cayenne.job
  (:import [java.util.concurrent Executors])
  (:import [java.util UUID]))

(def processing-pool 
  (Executors/newFixedThreadPool
   (->
    (.. Runtime getRuntime availableProcessors)
    (+ 2)
    (* 3))))

(def future-pool (ref {}))

(defn put-job [job] 
  (let [id (.toString (UUID/randomUUID))]
    (dosync 
      (alter future-pool assoc id (.submit processing-pool job)))
    id))

(defn forget-job [job-id]
  (dosync
    (alter future-pool dissoc job-id)))

(defn cancel-job [job-id]
  (.cancel (get @future-pool job-id) true))

(defn get-job-result [job-id]
  (-> @future-pool (get job-id) (.get)))

(defn get-job-status [job-id]
  (let [future (get @future-pool job-id)]
    (cond
     (nil? future) :inactive
     (.isDone future) :complete
     (.isCancelled future) :cancelled
     :else :waiting)))

(defn job-count []
  (count @future-pool))

;(defn list-all-jobs [] (keys @job-pool))
;(defn list-finished-jobs [] (filter #(.isDone %) @task-list))
;(defn list-unfinished-jobs [] (filter #(not (.isDone %)) @task-list))
;(defn list-cancelled-jobs [] (filter #(.isCancelled %) @task-list))
