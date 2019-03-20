(ns cayenne.api.route
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.routes :as v1]
            [cayenne.api.v1.doc :as v1-doc]
            [cayenne.api.v1.feed :as feed-v1]
            [cayenne.api.conneg :as conneg]
            [cayenne.api.auth.crossref :as cr-auth]
            [cayenne.api.auth.token :as token-auth]
            [cayenne.util :as util]
            [ring.middleware.logstash :as logstash]
            [heartbeat.ring :refer [wrap-heartbeat]]
            [heartbeat.core :refer [def-web-check def-version]]
            [liberator.dev :refer [wrap-trace]]
            [metrics.ring.expose :refer [expose-metrics-as-json]]
            [metrics.ring.instrument :refer [instrument]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace-web]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [compojure.handler :as handler]
            [ring.util.response :refer [redirect] :as response]
            [org.httpkit.server :as hs]
            [clojure.data.json :as json]
            [compojure.core :refer [wrap-routes defroutes routes context ANY]]))

(def-version cayenne.version/version)

(def-web-check :doi-unixref-query
  (str (conf/get-param [:upstream :unixref-url])
       (conf/get-param [:test :doi])))

(def-web-check :doi-unixsd-query
  (str (conf/get-param [:upstream :unixsd-url])
       (conf/get-param [:test :doi])))

(defn create-unprotected-api-routes []
  (routes
   v1/api-routes
   v1-doc/api-doc-routes
   (context "/v1" [] v1/api-routes)
   (context "/v1" [] v1-doc/api-doc-routes)
   (context "/v1.0" [] v1/api-routes)
   (context "/v1.0" [] v1-doc/api-doc-routes)))

(defn create-docs-routes []
  (routes
   (ANY "/help" []
        (redirect "https://github.com/CrossRef/rest-api-doc"))
   (ANY "/" [] 
        (redirect "/help"))))

(defn create-feed-routes []
  (wrap-routes
   (routes
    (context "/feeds" [] feed-v1/feed-api-routes)
    (context "/v1/feeds" [] feed-v1/feed-api-routes)
    (context "/v1.0/feeds" [] feed-v1/feed-api-routes))
   wrap-basic-authentication
   token-auth/authenticated?))

(defn create-unknown-route []
  (routes
   (ANY "*" []
        (-> (response/response
             (json/write-str
              {:status :error
               :message-type :route-not-found
               :message-version "1.0.0"
               :message "Route not found"}))
            (response/status 404)
            (response/header "Content-Type" "application/json")))))

(defn create-all-routes [& {:keys [feed-api]
                            :or {feed-api false}}]
  (apply routes
         (cond-> [(create-docs-routes)]
           feed-api (conj (create-feed-routes))
           true (conj (create-unprotected-api-routes))
           true (conj (create-unknown-route)))))

(defn wrap-cors
  [h]
  (fn [request]
    (-> (h request)
        (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
        (assoc-in [:headers "Access-Control-Allow-Headers"]
                  "X-Requested-With"))))

(defn wrap-exception-handler
  [h]
  (fn [request]
    (try
      (h request)
      (catch Exception e
        (-> (response/response
             (json/write-str
              {:status :error
               :message-type :exception
               :message-version "1.0.0"
               :message
               {:name (type e)
                :description (.toString e)
                :message (.getMessage e)
                :stack (map #(.toString %) (.getStackTrace e))
                :cause
                (when-let [cause (.getCause e)]
                  {:name (type cause)
                   :description (.toString cause)
                   :stack (map #(.toString %) (.getStackTrace e))
                   :message (.getMessage cause)})}}))
            (response/status 500)
            (response/header "Content-Type" "application/json")
            (response/header "Exception-Name" (type e)))))))

(defn wrap-ignore-trailing-slash
  [handler]
  (fn [request]
    (let [uri (:uri request)]
      (handler (assoc request :uri (if (and (not (= "/" uri))
                                            (.endsWith uri "/"))
                                     (subs uri 0 (dec (count uri)))
                                     uri))))))

(defn create-handler
  "Construct the handlers. Feed API can be enabled or otherwise for security."
  [& {:keys [feed-api] :or {feed-api false}}]
  (-> (create-all-routes :feed-api feed-api)
      (logstash/wrap-logstash :host (conf/get-param [:service :logstash :host])
                              :port (conf/get-param [:service :logstash :port])
                              :name (conf/get-param [:service :logstash :name]))
      (handler/api)
      (wrap-cors)
      (expose-metrics-as-json)
      (instrument)
      (wrap-heartbeat)
      ; (wrap-trace :ui)
      ; disabled due to bug in apache2 reverse proxy
      ; (creates headers that are incompatible)
      ; (wrap-stacktrace-web)
      (conneg/wrap-accept)
      (wrap-exception-handler)
      (wrap-ignore-trailing-slash)))

; Register a startup task in the default core.
; This task will register the a service called 'api', which is a running server.
(conf/with-core :default
  (conf/add-startup-task
   :api
   (fn [profiles]
     (conf/set-service! 
      :api
      (hs/run-server 
       (create-handler :feed-api (some #{:feed-api} profiles))
       {:join? false
        :thread 128
        :port (conf/get-param [:service :api :port])})))))
