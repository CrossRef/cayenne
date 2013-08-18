(ns cayenne.api.route
  (:import [java.net URL])
  (:require [cayenne.ids :as ids]
            [cayenne.conf :as conf]
            [cayenne.data.deposit :as d]
            [cayenne.data.object :as o]
            [cayenne.data.core :as c]
            [cayenne.api.types :as t]
            [clojure.data.json :as json]
            [liberator.core :refer [defresource resource]]
            [liberator.dev :refer [wrap-trace]]
            [compojure.core :refer [defroutes ANY]]))

(defn ->1
  "Helper that creates a function that calls f while itself taking one
   argument which is ignored."
  [f]
  (fn [_] (f)))

(defn abs-url 
  "Create an absolute url to a resource relative to the given request url. The
   request url acts as the base of a path created by concatentating paths."
  [request & paths]
  (URL. (format "%s://%s:%s%s/%s"
                (name (:scheme request))
                (:server-name request)
                (:server-port request)
                (:uri request)
                (clojure.string/join "/" paths))))

(defn content-type-matches 
  "True if the content type contained in ctx matches any of those listed
   in cts."
  [ctx cts]
  (let [ct (get-in ctx [:request :headers "content-type"])]
    (some #{ct} cts)))

(defresource deposits-resource
  :allowed-methods [:post]
  :known-content-type? #(content-type-matches % t/depositable)
  :post-redirect? true
  :post! #({:id (d/create! (:data %))})
  :location #(abs-url (:request %) (:id %)))

(defresource deposit-resource [id]
  :allowed-methods [:get]
  :available-media-types [t/deposit])
;  :delete!
;  :exists?
;  :handle-ok)

(defresource object-resource [uri]
  :allowed-methods [:get]
  :available-media-types [])
  ;:exists?
  ;:handle-ok)

(defresource object-instance-resource [uri instance]
  :allowed-methods [:get])
  ;:exists?
  ;:handle-ok)

(defresource prefix-resource [prefix])

(defresource cores-resource
  :allowed-methods [:get]
  :media-type-available? t/html-or-json
  :handle-ok (->1 #(json/write-str (c/fetch-all))))

(defresource core-resource [core-name]
  :allowed-methods [:get]
  :available-media-types t/html-or-json
  :exists? (->1 #(c/exists? core-name))
  :handle-ok (->1 #(json/write-str (c/fetch core-name))))

(defroutes api-routes
  (ANY "/cores" []
       cores-resource)
  (ANY "/cores/:name" [name]
       (core-resource name))
  (ANY "/objects/:type/:id" [type id]
       (object-resource (ids/get-id-uri type id)))
  (ANY "/objects/:uri" [uri]
       (object-resource uri))
  (ANY "/objects/:uri/:instance" [uri instance]
       (object-instance-resource uri instance))
  (ANY "/deposits" [] 
       deposits-resource)
  (ANY "/deposits/:id" [id]
       (deposit-resource id)))

(def api
  (-> api-routes
      (wrap-trace :ui)))

(conf/with-core :default 
  (conf/set-param! [:service :api :var] #'api))
