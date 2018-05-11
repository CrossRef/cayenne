(ns cayenne.tasks
  (:require [cayenne.tasks.publisher :as publisher]
            [cayenne.tasks.journal :as journal]
            [cayenne.tasks.funder :as funder]
            [cayenne.tasks.category :as category]
            [cayenne.tasks.coverage :as coverage]
            [cayenne.data.work :as work]
            [cayenne.action :as action]
            [cayenne.conf :as conf]
            [cayenne.production :as production]
            [clj-time.core :as time]
            [clj-time.format :as timef]))

(defn setup []
  (conf/create-core-from! :task :default)
  (conf/set-core! :task)
  (production/apply-env-overrides :task)
  (conf/start-core! :task))

(defn index-journals [& args]
  (setup)
  (journal/index-journals))
  ;; (coverage/check-journals collection-name))

(defn index-members [& args]
  (setup)
  (publisher/index-members))
;;    (coverage/check-members collection-name)))

(defn index-subjects [& args]
  (setup)
  (category/index-subjects))
