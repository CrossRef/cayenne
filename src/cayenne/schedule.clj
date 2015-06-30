(ns cayenne.schedule
  (:require [cayenne.action :as action]
            [cayenne.conf :as conf]
            [cayenne.tasks.solr :as solr]
            [cayenne.tasks.prefix :as prefix]
            [cayenne.tasks.funder :as funder]
            [cayenne.tasks.doaj :as doaj]
            [cayenne.tasks.category :as category]
            [cayenne.tasks.solr :as solr]
            [cayenne.tasks.publisher :as publisher]
            [cayenne.tasks.coverage :as coverage]
            [cayenne.tasks.journal :as journal]
            [cayenne.api.v1.feed :as feed]
            [clj-time.core :as time]
            [clj-time.format :as timef]
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.triggers :as qt]
            [clojurewerkz.quartzite.jobs :as qj]
            [clojurewerkz.quartzite.schedule.cron :as cron]
            [clojurewerkz.quartzite.schedule.simple :as simple]
            [taoensso.timbre :as timbre :refer [info error]])
   (:use [clojurewerkz.quartzite.jobs :only [defjob]]))

(def crossref-oai-services [:crossref-journals :crossref-books :crossref-serials])

(def oai-date-format (timef/formatter "yyyy-MM-dd"))

(def index-daily-work-trigger
  (qt/build
   (qt/with-identity (qt/key "index-daily-work"))
   (qt/with-schedule
     (cron/schedule
      (cron/cron-schedule "0 0 1 ? * *")))))

(def index-hourly-work-trigger
  (qt/build
   (qt/with-identity (qt/key "index-hourly-work"))
   (qt/with-schedule
     (cron/schedule
      (cron/cron-schedule "0 0 * * * ?")))))

(def update-members-daily-work-trigger
  (qt/build
   (qt/with-identity (qt/key "update-members-daily-work"))
   (qt/with-schedule
     (cron/schedule
      (cron/cron-schedule "0 0 1 ? * *")))))

(def update-journals-daily-work-trigger
  (qt/build
   (qt/with-identity (qt/key "update-journals-daily-work"))
   (qt/with-schedule
     (cron/schedule
      (cron/cron-schedule "0 0 2 ? * *")))))

(def process-feed-files-trigger
  (qt/build
   (qt/with-identity (qt/key "process-feed-files"))
   (qt/with-schedule
     (simple/schedule
      (simple/repeat-forever)
      (simple/with-interval-in-milliseconds 500)))))

(defjob index-crossref-oai [ctx]
  (let [from (time/minus (time/today-at-midnight) (time/days 3))
        until (time/today-at-midnight)]
    (info (str "Running index of CrossRef OAI from "
               from " until " until))
    (doseq [oai-service crossref-oai-services]
      (action/get-oai-records
       (conf/get-param [:oai oai-service])
       (timef/unparse oai-date-format from)
       (timef/unparse oai-date-format until)
       action/index-solr-docs))))

(defjob flush-solr-insert-list [ctx]
  (info "Flushing solr insert buffer")
  (solr/force-flush-insert-list))

(defjob update-members [ctx]
  (try
    (info "Updating members collection")
    (publisher/load-publishers "members")
    (catch Exception e (error e "Failed to update members collection")))
  (try
    (info "Updating member flags and coverage values")
    (coverage/check-members "members")
    (catch Exception e (error e "Failed to update member flags and coverage values"))))

(defjob update-journals [ctx]
  (try
    (info "Updating journals collection")
    (journal/load-journals-from-cr-title-list-csv "journals")
    (catch Exception e (error e "Failed to update journals collection")))
  (try
    (info "Updating journal flags and coverage values")
    (coverage/check-journals "journals")
    (catch Exception e (error e "Failed to update journal flags and coverage values"))))

(defjob process-feed-files [ctx]
  (try
    (feed/process-feed-files!)
    (catch Exception e (error e "Failed to process feed files"))))

(defn start []
  (qs/initialize)
  (qs/start))

(defn start-indexing []
  (qs/schedule
   (qj/build
    (qj/of-type index-crossref-oai)
    (qj/with-identity (qj/key "index-crossref-oai")))
    index-daily-work-trigger)
  (qs/schedule
   (qj/build
    (qj/of-type flush-solr-insert-list)
    (qj/with-identity (qj/key "flush-solr-insert-list")))
    index-hourly-work-trigger))

(defn start-members-updating []
  (qs/schedule
   (qj/build
    (qj/of-type update-members)
    (qj/with-identity (qj/key "update-members")))
   update-members-daily-work-trigger))

(defn start-journals-updating []
  (qs/schedule
   (qj/build
    (qj/of-type update-journals)
    (qj/with-identity (qj/key "update-journals")))
   update-journals-daily-work-trigger))

(defn start-processing-feed-files []
  (qs/schedule
   (qj/build
    (qj/of-type process-feed-files)
    (qj/with-identity (qj/key "process-feed-files")))
   process-feed-files-trigger))

(conf/with-core :default
  (conf/add-startup-task 
   :index
   (fn [profiles]
     (start-indexing))))

(conf/with-core :default
  (conf/add-startup-task
   :update-members
   (fn [profiles]
     (start-members-updating))))

(conf/with-core :default
  (conf/add-startup-task
   :update-journals
   (fn [profiles]
     (start-journals-updating))))

(conf/with-core :default
  (conf/add-startup-task
   :process-feed-files
   (fn [profiles]
     (start-processing-feed-files))))

