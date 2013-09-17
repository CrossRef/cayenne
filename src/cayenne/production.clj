(ns cayenne.production
  (:require [cayenne.conf :as conf]
            [cayenne.api.route :as route]
            [taoensso.timbre :as timbre]))

(defn- main []
  (timbre/set-config! [:appenders :standard-out :enabled?] false)
  (timbre/set-config! [:appenders :spit :enabled?] true)
  (timbre/set-config! [:shared-appender-config :spit-filename] "log/log.txt")
  
  (conf/create-core-from! :production :default)
  
  (conf/with-core :production
    (conf/set-param! [:env] :production))
  
  (conf/set-core! :production)
  
  (conf/start-core! :production))
