(ns cayenne.api.v1.routes
  (:import [java.net URL URLDecoder])
  (:require [cayenne.ids :as ids]
            [cayenne.ids.doi :as doi-id]
            [cayenne.ids.fundref :as fr-id]
            [cayenne.ids.prefix :as prefix-id]
            [cayenne.ids.member :as member-id]
            [cayenne.conf :as conf]
            [cayenne.data.deposit :as d]
            [cayenne.data.core :as c]
            [cayenne.data.work :as work]
            [cayenne.data.funder :as funder]
            [cayenne.data.prefix :as prefix]
            [cayenne.data.member :as member]
            [cayenne.data.type :as data-types]
            [cayenne.data.csl :as csl]
            [cayenne.data.license :as license]
            [cayenne.api.transform :as transform]
            [cayenne.api.link :as link]
            [cayenne.api.v1.types :as t]
            [cayenne.api.v1.query :as q]
            [cayenne.api.v1.parameters :as p]
            [cayenne.api.conneg :as conneg]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [liberator.core :refer [defresource resource]]
            [liberator.representation :refer [ring-response]]
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

(defresource csl-styles-resource
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(csl/fetch-all-styles)))

(defresource csl-locales-resource
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(csl/fetch-all-locales)))

(defresource cores-resource
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(c/fetch-all)))

(defresource core-resource [core-name]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? (->1 #(c/exists? core-name))
  :handle-ok (->1 #(c/fetch core-name)))

;; deposits todo
;; - Auth header authentication
;; - enforce https
;; - accept deposit types
;; - perform deposit for XML (as a retrying job)
;; - perform XML validation
;; - perform citation extraction and optional deposit XML construction

(defresource deposits-resource [data]
  :allowed-methods [:post :options]
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
  :media-type-available? (constantly true) ;; todo should return {:representation ...}
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
  :exists? (->1 #(when-let [work (-> doi
                                     (URLDecoder/decode)
                                     (doi-id/to-long-doi-uri)
                                     (work/fetch-one))]
                   {:work work}))
  :handle-ok :work)

(defresource work-health-resource [doi]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(work/fetch-quality doi)))

(defn force-exact-request-doi
  "Why? DOIs are case insensitive. CrossRef APIs try to always present DOIs
   lowercases, but out in the real world they may appear mixed-case. We want
   clients to be given RDF that describes the case-sensitive URI they requested,
   so we avoid the canonical lower-case DOI and present metadata for the DOI
   exactly as requested."
  [request doi]
  (assoc (get-in request [:work :message]) 
    :URL 
    (str "http://dx.doi.org/" (URLDecoder/decode doi))))

(defresource work-transform-resource [doi]
  :allowed-methods [:get :options]
  :media-type-available? (conneg/content-type-matches t/work-transform)
  :exists? (->1 #(when-let [work (-> doi
                                     (URLDecoder/decode)
                                     (doi-id/to-long-doi-uri)
                                     (work/fetch-one))]
                   {:work work}))
  :handle-ok #(let [links (link/make-link-headers
                           (get-in % [:work :message]))
                    headers (if-not (string/blank? links) {"Link" links} {})]
                (ring-response
                 {:headers headers
                  :body (transform/->format (:representation %)
                                            (force-exact-request-doi % doi))})))

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

(defresource prefix-resource [px]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [p (prefix/fetch-one
                          (q/->query-context % :id (prefix-id/to-prefix-uri px)))]
              {:publisher p})
  :handle-ok :publisher)

(defresource prefix-works-resource [px]
  :allowed-methods [:get :options]
  :malformed? p/malformed-list-request?
  :available-media-types t/json
  :handle-ok #(prefix/fetch-works (q/->query-context % :id (prefix-id/to-prefix-uri px))))

(defresource members-resource
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(member/fetch (q/->query-context %)))

(defresource member-resource [id]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [m (member/fetch-one
                          (q/->query-context % :id (member-id/to-member-id-uri id)))]
              {:member m})
  :handle-ok :member)

(defresource member-works-resource [id]
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [m (member/fetch-one
                          (q/->query-context % :id (member-id/to-member-id-uri id)))]
              {:member m})
  :handle-ok #(member/fetch-works (q/->query-context % :id (member-id/to-member-id-uri id))))

(defresource licenses-resource
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(license/fetch-all (q/->query-context %)))

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
  (ANY "/licenses" []
       licenses-resource)
  (ANY "/styles" []
       csl-styles-resource)
  (ANY "/locales" []
       csl-locales-resource)
  (ANY "/funders" []
       funders-resource)
  (ANY "/funders/*" {{id :*} :params}
       (if (.endsWith id "/works")
         (funder-works-resource (string/replace id #"/works\z" ""))
         (funder-resource id)))
  (ANY "/members" []
       members-resource)
  (ANY "/members/:id" [id]
       (member-resource id))
  (ANY "/members/:id/works" [id]
       (member-works-resource id))
  (ANY "/prefixes" []
       "Not implemented.")
  (ANY "/prefixes/:prefix" [prefix]
       (prefix-resource prefix))
  (ANY "/prefixes/:prefix/works" [prefix]
       (prefix-works-resource prefix))
  (ANY "/works" []
       works-resource)
  (ANY "/works/*" {{doi :*} :params}
       (cond (.endsWith doi "/quality")
             (work-health-resource (string/replace doi #"/quality\z" ""))
             (.endsWith doi "/transform")
             (work-transform-resource (string/replace doi #"/transform\z" ""))
             :else
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
