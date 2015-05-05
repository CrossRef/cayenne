(ns cayenne.production
  (:gen-class :main true)
  (:require [cayenne.conf :as conf]
            [cayenne.api.route :as route]
            [taoensso.timbre :as timbre]
            [cayenne.api.route]
            [cayenne.schedule :as schedule]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]
            [org.httpkit.client :as http]))

(defn slack-format
  [{:keys [level ns throwable message]}]
  (format "_%s_ `%s` %s%s"
          (-> level name)
          ns
          (if message
            (with-out-str (pprint message))
            "")
          (if throwable
            (str "\n" (.toString throwable))
            "")))

(defn send-to-slack [log-event]
  (let [payload {"username" "cayennebot"
                 "icon_emoji" ":ghost:"
                 "text" (slack-format log-event)}]
    (http/post (conf/get-param [:upstream :slack-logging])
               {:form-params {:payload (json/write-str payload)}})))

(def timbre-slack
  {:doc "Spits to #cayenne slack channel."
   :enabled? true
   :async? true
   :fn send-to-slack})

(def termination (promise))

(defn -main [& args]
  (let [profiles (map #(->> % (drop 1) (apply str) keyword) args)]

    (timbre/set-config! [:appenders :standard-out :enabled?] false)
    (timbre/set-config! [:appenders :spit :enabled?] true)
    (timbre/set-config! [:shared-appender-config :spit-filename] "log/log.txt")

    (when (conf/get-param [:upstream :slack-logging])
      (timbre/set-config! [:appenders :slack] timbre-slack))

    (schedule/start)
    
    (conf/create-core-from! :production :default)
    (conf/set-core! :production)
    (apply (partial conf/start-core! :production) profiles)
    
    @termination))
  
(defn stop []
  (deliver termination true))
