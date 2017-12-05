(ns cayenne.production
  (:gen-class :main true)
  (:require [cayenne.conf :as conf]
            [cayenne.api.route :as route]
            [taoensso.timbre :as timbre]
            [cayenne.api.route]
            [cayenne.schedule :as schedule]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]
            [org.httpkit.client :as http]
            [taoensso.timbre :as timbre :refer [info error]]
            [environ.core :refer [env]]))

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

(defn apply-env-overrides
  "Take some config values from environment variables. Overrides
   some parameter values in the given core."
  [core-name]
  (conf/with-core core-name
    (when (env :references)
      (if (some #{(env :references)} ["open" "limited" "closed"])
        (conf/set-param! [:service :api :references] (env :references))
        (error (str "Unknown references setting " (env :references)))))
    (when (env :mongo-host)
      (conf/set-param! [:service :mongo :host] (env :mongo-host)))
    (when (env :solr-host)
      (conf/set-param! [:service :solr :url]
                       (str "http://" (env :solr-host) ":8983/solr/crmds1"))
      (conf/set-param! [:service :solr :update-list]
                       [{:url (str "http://" (env :solr-host) ":8983/solr")
                         :core "crmds1"}]))
    (when (env :api-port)
      (conf/set-param! [:service :api :port]
                       (Integer/parseInt (env :api-port))))
    (when (env :nrepl-port)
      (conf/set-param! [:service :nrepl :port]
                       (Integer/parseInt (env :nrepl-port))))
    (when (env :oai-path)
      (conf/set-param! [:oai :crossref-test :url] (env :oai-path))
      (conf/set-param! [:oai :crossref-journals :url] (env :oai-path))
      (conf/set-param! [:oai :crossref-books :url] (env :oai-path))
      (conf/set-param! [:oai :crossref-serials :url] (env :oai-path)))))

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

    (apply-env-overrides :production)
    
    (apply (partial conf/start-core! :production) profiles)
    
    @termination))
  
(defn stop []
  (deliver termination true))
