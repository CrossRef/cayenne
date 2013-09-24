(ns cayenne.job
  (:import [java.util.concurrent Executors])
  (:import [java.util UUID])
  (:use [clojure.core.incubator])
  (:require [cayenne.conf :as conf]
            [metrics.counters :refer [defcounter] :as counters]
            [metrics.gauges :refer [defgauge]]))

(def processing-mul 1)

(def processing-pool 
  (Executors/newFixedThreadPool
   (->
    (.. Runtime getRuntime availableProcessors)
    (+ 2)
    (* processing-mul))))

(def job-id->future (atom {}))

(defcounter [cayenne jobs waiting])
(defcounter [cayenne jobs completed])
(defcounter [cayenne jobs failed])

(defgauge [cayenne jobs running]
  (- (count @job-id->future)
     (counters/value waiting)))

(defn make-job [p & {:keys [success fail exception]
                     :or {success (constantly nil)
                          exception (constantly nil)}}]
  {:process p
   :success-handler success
   :exception-handler exception})

(defn forget-job [job-id]
  (swap! job-id->future dissoc job-id))

(defn cancel-job [job-id]
  (when-let [job-future (get @job-id->future job-id)]
    (.cancel job-future)))

(defn put-job [meta job]
  (let [id (.toString (UUID/randomUUID))
        job-fn (fn []
                 (try
                   (counters/dec! waiting)
                   ((:process job))
                   (counters/inc! completed)
                   ((:success-handler job) job meta)
                   (catch Exception e
                     (counters/inc! failed)
                     ((:exception-handler job) job meta e)))
                 (forget-job id))]
    (counters/inc! waiting)
    (swap! job-id->future assoc id (.submit processing-pool job-fn))
    id))

