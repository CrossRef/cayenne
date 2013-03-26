(ns cayenne.job
  (:import [java.util.concurrent Executors])
  (:import [java.util UUID]))

(def processing-pool 
  (Executors/newFixedThreadPool
   (->
    (.. Runtime getRuntime availableProcessors)
    (+ 2)
    (* 3))))

(def job-pool (ref (sorted-map)))

(defn put-job [job] 
  (let [id (.toString (UUID/randomUUID))]
    (dosync 
      (alter job-pool assoc id (.submit processing-pool job)))
    id))

(defn forget-job [job-id]
  (dosync
    (alter job-pool dissoc job-id)))

(defn cancel-job [job-id]
  (.cancel (get @job-pool job-id) true))

(defn list-all-jobs [] (keys @job-pool))
;(defn list-finished-jobs [] (filter #(.isDone %) @task-list))
;(defn list-unfinished-jobs [] (filter #(not (.isDone %)) @task-list))
;(defn list-cancelled-jobs [] (filter #(.isCancelled %) @task-list))
