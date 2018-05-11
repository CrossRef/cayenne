(ns cayenne.user
  (:require [clojure.string :as str]
            [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi-id]
            [cayenne.schedule :as schedule]
            [cayenne.api.route :as route]
            [cayenne.action :as action]
            [taoensso.timbre :as timbre]
            [cayenne.tasks.category :as category]
            [cayenne.tasks.journal :as journal]
            [cayenne.tasks.publisher :as publisher]
            [cayenne.tasks.funder :as funder]
            [cayenne.data.member :as member]))

(defn begin [& profiles]
  (timbre/set-config! [:appenders :standard-out :enabled?] false)
  (timbre/set-config! [:appenders :spit :enabled?] true)
  (timbre/set-config! [:shared-appender-config :spit-filename] "log/log.txt")

  (schedule/start)
  
  (conf/create-core-from! :user :default)
  (conf/set-core! :user)
  (apply conf/start-core! :user profiles))

(defn status []
  (println
   (str "Status = " (conf/get-param [:status])))
  (println
   (str "Running services = "
        (str/join ", " (-> @conf/cores
                           (get-in [conf/*core-name* :services])
                           keys)))))

(defn index-ancillary []
  (category/index-subjects)
  (publisher/index-members)
  (journal/index-journals)
  (category/update-journal-subjects))

