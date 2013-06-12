(ns cayenne.job
  (:import [java.util.concurrent Executors])
  (:import [java.util UUID])
  (:use [clojure.core.incubator])
  (:require [cayenne.conf :as conf]))

(def processing-pool 
  (Executors/newFixedThreadPool
   (->
    (.. Runtime getRuntime availableProcessors)
    (+ 2)
    (* 3))))

(def job-id-set-map (ref {}))
(def set-future-pool (ref {}))

(defn make-job [p & {:keys [success fail exception]
                     :or {success (constantly nil)
                          fail (constantly nil)
                          exception (constantly nil)}}]
  {:process p
   :success-handler success
   :fail-handler fail
   :exception-handler exception})

(defn get-set [job-id]
  (get @job-id-set-map job-id))

(defn forget-job [job-id]
  (let [set-name (get-set job-id)]
    (dosync
     (alter set-future-pool dissoc-in [set-name job-id])
     (alter job-id-set-map dissoc job-id))))

(defn cancel-job [job-id]
  (.cancel (get-in @set-future-pool [(get-set job-id) job-id])))

(defn cancel-set [set-name]
  (dosync
   (doseq [[job-id future] (get @set-future-pool set-name)]
     (.cancel future)
     (forget-job future))
   (alter set-future-pool dissoc set-name)))

(defn job-count [set-name]
  (count (keys (get @set-future-pool set-name))))

(defn put-job [set-name meta job]
  (let [id (.toString (UUID/randomUUID))
        job-fn (fn [] 
                 (conf/with-result-set set-name
                   (conf/with-result-job id
                     (try
                       (if ((:process job))
                         ((:success-handler job) job meta)
                         ((:fail-handler job) job meta))
                       (forget-job id)
                       (catch Exception e
                         ((:exception-handler job) job meta e))))))]
    (dosync 
     (let [future (.submit processing-pool job-fn)]
       (alter set-future-pool assoc [set-name id] future)
       (alter job-id-set-map assoc id set-name)))
    id))

