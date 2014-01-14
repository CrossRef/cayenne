(ns cayenne.schedule
  (:require [cayenne.action :as action]
            [cayenne.conf :as conf]
            [cayenne.tasks.solr :as solr]
            [cayenne.tasks.prefix :as prefix]
            [cayenne.tasks.funder :as funder]
            [cayenne.tasks.doaj :as doaj]
            [cayenne.tasks.category :as category]
            [clj-time.core :as time]
            [clj-time.format :as timef]
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.triggers :as qt]
            [clojurewerkz.quartzite.jobs :as qj]
            [clojurewerkz.quartzite.schedule.cron :as cron]
            [taoensso.timbre :as timbre :refer [info error]])
   (:use [clojurewerkz.quartzite.jobs :only [defjob]]))

(def crossref-oai-services [:crossref-journals :crossref-books :crossref-serials])

(def oai-date-format (timef/formatter "yyyy-MM-dd"))

(def daily-work-trigger
  (qt/build
   (qt/with-identity (qt/key "daily-work"))
   (qt/with-schedule
     (cron/schedule
      (cron/cron-schedule "0 0 1 ? * *")))))

(defjob index-crossref-oai [ctx]
  (let [from (time/minus (time/today-at-midnight) (time/days 2))
        until (time/today-at-midnight)]
    (info (str "Running index of CrossRef OAI from "
               from " until " until))
    (doseq [oai-service crossref-oai-services]
      (action/get-oai-records
       (conf/get-param [:oai oai-service])
       (timef/unparse oai-date-format from)
       (timef/unparse oai-date-format until)
       action/index-solr-docs))))

(defn start []
  (qs/initialize)
  (qs/start))

(defn start-indexing []
  (qs/schedule
   (qj/build
    (qj/of-type index-crossref-oai)
    (qj/with-identity (qj/key "index-crossref-oai")))
    daily-work-trigger))

(conf/with-core :default
  (conf/add-startup-task 
   :index
   (fn [profiles]
     (start)
     (start-indexing))))
