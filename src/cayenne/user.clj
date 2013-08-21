(ns cayenne.user
  (:require [cayenne.conf :as conf]
            [cayenne.api.route :as route]
            [cayenne.action :as action]))

(conf/create-core-from! :user :default)

(conf/with-core :user
  (conf/set-param! [:env] :user))

(conf/set-core! :user)

(conf/start-core! :user)
