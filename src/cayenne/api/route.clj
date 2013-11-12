(ns cayenne.api.route
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.routes :as v1]
            [cayenne.api.v1.doc :as v1-doc]
            [liberator.dev :refer [wrap-trace]]
            [metrics.ring.expose :refer [expose-metrics-as-json]]
            [metrics.ring.instrument :refer [instrument]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace-web]]
            [compojure.handler :as handler]
            [ring.util.response :refer [redirect]]
            [compojure.core :refer [defroutes routes context ANY]]))

(def all-routes
  (routes
   v1/api-routes
   v1-doc/api-doc-routes
   (context "/v1" [] v1/api-routes)
   (context "/v1" [] v1-doc/api-doc-routes)
   (context "/v1.0" [] v1/api-routes)
   (context "/v1.0" [] v1-doc/api-doc-routes)

   ;; legacy urls
   (ANY "/help" []
        (redirect "https://github.com/CrossRef/fundrefplus_doc/blob/master/funder_kpi_api.md"))
   (ANY "/funder_kpi_metadata_best_practice.html" []
        (redirect "http://fundref.crossref.org/docs/funder_kpi_metadata_best_practice.html"))
   (ANY "/" [] 
        (redirect "http://www.crossref.org/fundref"))))

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
      ;(wrap-trace :ui)
      ; disabled due to bug in apache2 reverse proxy
      ; (creates headers that are incompatible)
      (wrap-stacktrace-web)))

(conf/with-core :default 
  (conf/set-param! [:service :api :var] #'api))
