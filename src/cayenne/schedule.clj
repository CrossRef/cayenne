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
            [clojurewerkz.quartzite.schedule.daily-interval :as daily]
            [clojurewerkz.quartzite.schedule.calendar-interval :as cal])
   (:use [clojurewerkz.quartzite.jobs :only [defjob]]))

(def crossref-oai-services [:crossref-journals :crossref-books :crossref-serials])

(def daily-work-trigger 
  (qt/build
   (qt/with-identity (qt/key "daily-work"))
   (qt/with-schedule
     (daily/schedule
      (daily/starting-daily-at (daily/time-of-day 20 0 0))))))

(def weekly-work-trigger
  (qt/build
   (qt/with-identity (qt/key "weekly-work"))
   (qt/with-schedule
     (cal/schedule
      (cal/with-interval-in-days 7)))))

;; afterwards call flush-insert-list, then solr commit, then solr swap cores
(defjob IndexCrossrefOai [ctx]
  (let [formatter (timef/formatter "YYYY-MM-dd")
        until (time/today-at-midnight)
        from (time/minus until (time/days 1))]
    (doseq [oai-service crossref-oai-services]
      (action/get-unixref-records 
       (conf/get-param [:oai oai-service])
       (timef/unparse formatter from)
       (timef/unparse formatter until)
       action/index-solr-docs
       solr/flush-commit-swap))))

(defjob ReindexLiveFundref [ctx]
  (-> (conf/get-param [:upstream :fundref-dois-live])
      (conf/remote-file)
      (action/reindex-fundref)))

(defjob ReindexDevFundref [ctx]
  (-> (conf/get-param [:upstream :fundref-dois-dev])
      (conf/remote-file)
      (action/reindex-fundref)))
 
(defjob RefreshCategoryCache [ctx]
  (category/clear!))

(defjob RefreshDoajCache [ctx]
  (doaj/clear!))

(defjob RefreshPrefixCache [ctx]
  (prefix/clear!))

(defjob ReloadFunders [ctx]
  (-> (conf/get-param [:upstream :fundref-registry])
      (conf/remote-file))
  (funder/clear!))

(defn start []
  (qs/initialize)
  (qs/start)
  (qs/schedule
   (qj/build
    (qj/of-type IndexCrossrefOai)
    (qj/with-identity (qj/key "index-crossref-oai")))
   daily-work-trigger)
  (qs/schedule
   (qj/build
    (qj/of-type RefreshCategoryCache)
    (qj/with-identity (qj/key "refresh-category-cache")))
   weekly-work-trigger)
  (qs/schedule
   (qj/build
    (qj/of-type RefreshDoajCache)
    (qj/with-identity (qj/key "refresh-doaj-cache")))
   weekly-work-trigger)
  (qs/schedule
   (qj/build
    (qj/of-type RefreshPrefixCache)
    (qj/with-identity (qj/key "refresh-prefix-cache")))
   weekly-work-trigger)
  (qs/schedule
   (qj/build
    (qj/of-type ReloadFunders)
    (qj/with-identity (qj/key "reload-funders")))
   weekly-work-trigger))
