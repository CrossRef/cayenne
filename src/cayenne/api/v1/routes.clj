(ns cayenne.api.v1.routes
  (:import [java.net URL URLDecoder])
  (:require [cayenne.ids :as ids]
            [cayenne.ids.doi :as doi-id]
            [cayenne.ids.fundref :as fr-id]
            [cayenne.ids.prefix :as prefix]
            [cayenne.conf :as conf]
            [cayenne.data.deposit :as d]
            [cayenne.data.core :as c]
            [cayenne.data.work :as work]
            [cayenne.data.funder :as funder]
            [cayenne.data.publisher :as publisher]
            [cayenne.data.program :as program]
            [cayenne.data.type :as data-types]
            [cayenne.api.v1.types :as t]
            [cayenne.api.v1.query :as q]
            [cayenne.api.v1.parameters :as p]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [liberator.core :refer [defresource resource]]
            [compojure.core :refer [defroutes routes context ANY]]))

(extend java.util.Date json/JSONWriter {:-write #(json/write (.toString %1) %2)})
(extend org.bson.types.ObjectId json/JSONWriter {:-write #(json/write (.toString %1) %2)})
(extend clojure.lang.Var json/JSONWriter {:-write #(json/write (.toString %1) %2)})
(extend java.lang.Object json/JSONWriter {:-write #(json/write (.toString %1) %2)})

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

(defresource cores-resource
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(c/fetch-all)))

(defresource core-resource [core-name]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? (->1 #(c/exists? core-name))
  :handle-ok (->1 #(c/fetch core-name)))

(defresource deposits-resource [data]
  :allowed-methods [:post :optionsx]
  :known-content-type? #(content-type-matches % t/depositable)
  :available-media-types t/json
  :post-redirect? #(hash-map :location (abs-url (:request %) (:id %)))
  :post! #(hash-map :id (d/create! (get-in % [:request :headers "content-type"]) data)))

(defresource deposit-resource [id]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? (->1 #(when-let [deposit (d/fetch id)] {:deposit deposit}))
  :handle-ok :deposit)

(defresource deposit-data-resource [id]
  :allowed-methods [:get :options]
  :media-type-available? (constantly true)
  :exists? (->1 #(when-let [deposit (d/fetch id)] {:deposit deposit}))
  :handle-ok (->1 #(d/fetch-data id)))

(defresource works-resource
  :allowed-methods [:get :options]
  :available-media-types t/json
  :malformed? p/malformed-list-request?
  :handle-ok #(work/fetch (q/->query-context %)))

(defresource work-resource [doi]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(-> doi
                       (URLDecoder/decode)
                       (doi-id/to-long-doi-uri)
                       (work/fetch-one))))

(defresource work-health-resource [doi]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(work/fetch-quality doi)))

(defresource funders-resource
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(funder/fetch (q/->query-context %)))

(defresource funder-resource [funder-id]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [f (funder/fetch-one 
                          (q/->query-context % :id (fr-id/id-to-doi-uri funder-id)))]
              {:funder f})
  :handle-ok :funder)

(defresource funder-works-resource [funder-id]
  :allowed-methods [:get :options]
  :malformed? p/malformed-list-request?
  :available-media-types t/json
  :handle-ok #(funder/fetch-works (q/->query-context % :id (fr-id/id-to-doi-uri funder-id))))

(defresource publishers-resource
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(publisher/fetch (q/->query-context %)))

(defresource publisher-resource [px]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [p (publisher/fetch-one
                          (q/->query-context % :id (prefix/to-prefix-uri px)))]
              {:publisher p})
  :handle-ok :publisher)

(defresource publisher-works-resource [px]
  :allowed-methods [:get :options]
  :malformed? p/malformed-list-request?
  :available-media-types t/json
  :handle-ok #(publisher/fetch-works (q/->query-context % :id (prefix/to-prefix-uri px))))

(defresource types-resource
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(data-types/fetch-all)))

(defresource type-resource [id]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [t (data-types/fetch-one (q/->query-context % :id id))]
              {:data-type t})
  :handle-ok :data-type)

(defresource type-works-resource [id]
  :allowed-methods [:get :options]
  :malformed? p/malformed-list-request?
  :available-media-types t/json
  :handle-ok #(data-types/fetch-works (q/->query-context % :id id)))

(defroutes api-routes
  (ANY "/funders" []
       funders-resource)
  (ANY "/funders/*" {{id :*} :params}
       (if (.endsWith id "/works")
         (funder-works-resource (string/replace id #"/works\z" ""))
         (funder-resource id)))
  (ANY "/publishers" []
       publishers-resource)
  (ANY "/publishers/:prefix" [prefix]
       (publisher-resource prefix))
  (ANY "/publishers/:prefix/works" [prefix]
       (publisher-works-resource prefix))
  (ANY "/works" []
       works-resource)
  (ANY "/works/*" {{doi :*} :params}
       (if (.endsWith doi "/quality")
         (work-health-resource (string/replace doi #"/quality\z" ""))
         (work-resource doi)))
  (ANY "/types" []
       types-resource)
  (ANY "/types/:id" [id]
       (type-resource id))
  (ANY "/types/:id/works" [id]
       (type-works-resource id))
  (ANY "/cores" []
       cores-resource)
  (ANY "/cores/:name" [name]
       (core-resource name))
  (ANY "/deposits" {body :body}
       (deposits-resource body))
  (ANY "/deposits/:id" [id]
       (deposit-resource id))
  (ANY "/deposits/:id/data" [id]
       (deposit-data-resource id)))
