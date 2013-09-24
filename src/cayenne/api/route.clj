(ns cayenne.api.route
  (:require [cayenne.api.v1.routes :as v1]
            [cayenne.api.v1.doc :as v1-doc]
            [compojure.core :refer [defroutes routes context ANY]]))

(def all-routes
  (routes
   (context "/v1" [] v1/api-routes)
   (context "/v1" [] v1-doc/api-doc-routes)))

(defn wrap-cors
  [h]
  (fn [request]
    (-> (h request)
        (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
        (assoc-in [:headers "Access-Control-Allow-Headers"]
                  "X-Requested-With"))))

(def api
  (-> all-routes
      (handler/api)
      (wrap-cors)
      (expose-metrics-as-json)
      (instrument)
      (wrap-trace :ui)
      (wrap-stacktrace-web)))

(conf/with-core :default 
  (conf/set-param! [:service :api :var] #'api))
