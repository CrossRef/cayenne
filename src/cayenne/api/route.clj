(ns cayenne.api.route
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.routes :as v1]
            [cayenne.api.v1.doc :as v1-doc]
            [cayenne.api.v1.graph :as graph-v1]
            [cayenne.api.v1.feed :as feed-v1]
            [cayenne.api.conneg :as conneg]
            [cayenne.api.auth.crossref :as cr-auth]
            [cayenne.api.auth.token :as token-auth]
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

(defn create-protected-api-routes []
  (wrap-routes
   (routes
    v1/restricted-api-routes
    (context "/v1" [] v1/restricted-api-routes)
    (context "/v1.0" [] v1/restricted-api-routes))
   wrap-basic-authentication
   cr-auth/authenticated?))

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

(defn create-graph-routes []
  (routes
   (context "/graph" [] graph-v1/graph-api-routes)
   (context "/v1/graph" [] graph-v1/graph-api-routes)
   (context "/v1.0/graph" [] graph-v1/graph-api-routes)))

(defn create-feed-routes []
  (wrap-routes
   (routes
    (context "/feeds" [] feed-v1/feed-api-routes)
    (context "/v1/feeds" [] feed-v1/feed-api-routes)
    (context "/v1.0/feeds" [] feed-v1/feed-api-routes))
   wrap-basic-authentication
   token-auth/authenticated?))

(defn create-all-routes [& {:keys [graph-api feed-api]
                            :or {graph-api false
                                 feed-api false}}]
  (apply routes
         (cond-> [(create-protected-api-routes)
                  (create-docs-routes)]
           graph-api (conj (create-graph-routes))
           feed-api (conj (create-feed-routes))
           true (conj (create-unprotected-api-routes)))))

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

(defn create-handler [& {:keys [graph-api feed-api] :or {graph-api false
                                                         feed-api false}}]
  (-> (create-all-routes :graph-api graph-api :feed-api feed-api)
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

(conf/with-core :default
  (conf/add-startup-task
   :api
   (fn [profiles]
     (when (some #{:graph} profiles)
       (require 'cayenne.tasks.datomic)
       (cayenne.tasks.datomic/connect!))
     (conf/set-service! 
      :api 
      (hs/run-server 
       (create-handler :graph-api (some #{:graph-api} profiles)
                       :feed-api (some #{:feed-api} profiles))
       {:join? false
        :thread 128
        :port (conf/get-param [:service :api :port])})))))
