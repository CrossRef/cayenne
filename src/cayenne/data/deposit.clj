(ns cayenne.data.deposit
  (:import [java.util Date])
  (:require [somnium.congomongo :as m]
            [cayenne.api.ingest :as ingest]
            [cayenne.conf :as conf]))

(defn create! [type deposit]
  (m/with-mongo (conf/get-service :mongo)
    (m/insert! :deposits
               {:processed false
                :type type
                :created-at (Date.)})))
                
(defn create? [deposit]
  ())

(defn get-by-id [id]
  (m/with-mongo (conf/get-service :mongo)
    (m/fetch-one :deposits id)))

