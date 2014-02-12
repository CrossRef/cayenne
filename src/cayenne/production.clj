(ns cayenne.production
  (:gen-class)
  (:require [cayenne.conf :as conf]
            [cayenne.api.route :as route]
            [taoensso.timbre :as timbre]
            [cayenne.api.route]
            [cayenne.schedule :as schedule]))

(def termination (promise))

(defn -main [& args]
  (let [profiles (map #(->> % (drop 1) (apply str) keyword) args)]

    (timbre/set-config! [:appenders :standard-out :enabled?] false)
    (timbre/set-config! [:appenders :spit :enabled?] true)
    (timbre/set-config! [:shared-appender-config :spit-filename] "log/log.txt")

    (schedule/start)
    
    (conf/create-core-from! :production :default)
    (conf/set-core! :production)
    (apply (partial conf/start-core! :production) profiles)
    
    @termination))
  
(defn stop []
  (deliver termination true))
