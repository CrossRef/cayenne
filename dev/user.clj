(ns user
  (:require [cayenne.conf :refer [start-core! stop-core!]]
            [clojure.java.shell :refer [sh]]))

(defn reset []
  (stop-core! :default)
  (start-core! :default :api))
