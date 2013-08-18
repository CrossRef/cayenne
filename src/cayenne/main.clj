(ns cayenne.main
  (:gen-class)
  (:require [cayenne.api.route :as route]
            [cayenne.conf :as conf]))

(defn -main [& args]
  (let [context (keyword (or (System/getenv "CONTEXT") "production"))]
    (conf/create-core-from! context :default)
    (conf/with-core context 
      (conf/set-param! [:env] context)
      (conf/set-param! [:service :api :port] 3001))
    (conf/set-core! context)
    (conf/start-core! context)))
  
