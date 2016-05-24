(ns cayenne.user
  (:require [clojure.string :as str]
            [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi-id]
            [cayenne.schedule :as schedule]
            [cayenne.api.route :as route]
            [cayenne.action :as action]
            [taoensso.timbre.appenders.irc :as irc-appender]
            [taoensso.timbre :as timbre])
  (:import [org.apache.solr.client.solrj SolrQuery]))

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

(defn print-solr-doi [doi]
  (-> (conf/get-service :solr)
      (.query 
       (doto (SolrQuery.)
         (.setQuery (str "doi_key:\"" (doi-id/to-long-doi-uri doi) "\""))))
      (.getResults)
      first
      prn)
  nil)

