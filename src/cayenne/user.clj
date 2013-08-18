(ns cayenne.user
  (:require [cayenne.conf :as conf]
            [cayenne.api.route :as route]))

(conf/create-core-from! :user :default)

(conf/with-core :user
  (conf/set-param! [:env] :user))

(conf/set-core! :user)

(conf/start-core! :user)
