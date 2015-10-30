(ns cayenne.api.v1.routes
  (:import [java.net URL URLDecoder])
  (:require [cayenne.ids :as ids]
            [cayenne.ids.doi :as doi-id]
            [cayenne.ids.fundref :as fr-id]
            [cayenne.ids.prefix :as prefix-id]
            [cayenne.ids.member :as member-id]
            [cayenne.ids.issn :as issn-id]
            [cayenne.conf :as conf]
            [cayenne.data.deposit :as d]
            [cayenne.data.core :as c]
            [cayenne.data.work :as work]
            [cayenne.data.funder :as funder]
            [cayenne.data.prefix :as prefix]
            [cayenne.data.member :as member]
            [cayenne.data.journal :as journal]
            [cayenne.data.type :as data-types]
            [cayenne.data.csl :as csl]
            [cayenne.data.license :as license]
            [cayenne.api.transform :as transform]
            [cayenne.api.link :as link]
            [cayenne.api.deposit :as dc]
            [cayenne.api.v1.types :as t]
            [cayenne.api.v1.query :as q]
            [cayenne.api.v1.parameters :as p]
            [cayenne.api.v1.validate :as v]
            [cayenne.api.conneg :as conneg]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [liberator.core :refer [defresource resource]]
            [liberator.representation :refer [ring-response]]
            [ring.util.response :refer [redirect]]
            [compojure.core :refer [defroutes routes context ANY]]))

(extend java.util.Date json/JSONWriter {:-write #(json/write (.toString %1) %2)})
(extend org.bson.types.ObjectId json/JSONWriter {:-write #(json/write (.toString %1) %2)})
(extend clojure.lang.Var json/JSONWriter {:-write #(json/write (.toString %1) %2)})
(extend java.lang.Object json/JSONWriter {:-write #(json/write (.toString %1) %2)})

(defn authed?
  "Has this request been successfully authenticated?"
  [context]
  (not (nil? (get-in context [:request :basic-authentication]))))

(defn get-owner [context]
  (get-in context [:request :basic-authentication 0]))

(defn get-passwd [context]
  (get-in context [:request :basic-authentication 1]))

(defn known-post-type? 
  "Does the content type submitted match a known content type, if the
   method is POST? Otherwise, if not method POST, accept the request
   regardless of content type."
  [context post-types]
  (let [method (get-in context [:request :request-method])]
    (if (not= :post method)
      true
      (some #{(get-in context [:request :headers "content-type"])} post-types))))

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

(defn rel-url
  [& paths]
  (str "/" (clojure.string/join "/" paths)))

(defn truth-param [context param-name]
  (if (#{"t" "true" "1"}
       (-> context
           (get-in [:request :params param-name])
           (or "false")))
    true
    false))

(defn param [context param-name]
  (get-in context [:request :params param-name]))

(defresource csl-styles-resource
  :malformed? (v/malformed?)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(csl/fetch-all-styles)))

(defresource csl-locales-resource
  :malformed? (v/malformed?)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(csl/fetch-all-locales)))

(defresource cores-resource
  :malformed? (v/malformed?)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(c/fetch-all)))

(defresource core-resource [core-name]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? (->1 #(c/exists? core-name))
  :handle-ok (->1 #(c/fetch core-name)))

;; This resource looks a little odd because we are trying to handle
;; any exception that comes about due to the post! action. If there
;; is an exception, we return a 400.

(defn deposit-failure [exception]
  (ring-response
   {:status 400
    :body 
    {:status :failed
     :message-type :entity-parsing-failure
     :message {:exception (.toString exception)}}}))

(defresource deposits-resource [data]
  :malformed? (v/malformed? :filter-validator v/validate-deposit-filters)
  :handle-malformed :validation-result
  :authorized? authed?
  :known-content-type? #(known-post-type? % t/depositable)
  :allowed-methods [:get :post :options]
  :available-media-types t/json
  :handle-ok #(d/fetch (q/->query-context % :filters {:owner (get-owner %)}))
  :post-redirect? #(hash-map :location (rel-url "deposits" (:id %)))
  :handle-see-other #(if (:id %)
                       (ring-response
                        {:status 303
                         :body ""
                         :headers {"Location"
                                   (rel-url "deposits" (:id %))}})
                       (deposit-failure (:ex %)))
  :post! #(try (hash-map :id (-> (dc/make-deposit-context
                               data
                               (get-in % [:request :headers "content-type"])
                               (get-owner %)
                               (get-passwd %)
                               (truth-param % :test)
                               (param % :pingback)
                               (param % :url)
                               (param % :filename)
                               (param % :parent))
                              (dc/deposit!)))
               (catch Exception e {:ex e})))

(defresource deposit-resource [id]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :authorized? authed?
  :allowed-methods [:get :post :options]
  :available-media-types t/json
  :exists? #(when-let [deposit 
                       (-> % 
                           (q/->query-context 
                            :filters {:owner (get-owner %)}
                            :id id)
                           (d/fetch-one))]
              {:deposit deposit})
  :handle-ok :deposit
  :post! #(do
            (->> (get-in % [:request :body])
                 (.bytes)
                 slurp
                 (d/modify! id))))

(defresource deposit-data-resource [id]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :authorized? authed?
  :allowed-methods [:get :options]
  :media-type-available? (constantly true) ;; todo should return {:representation ...}
  :exists? #(when-let [deposit (d/fetch-one 
                                (q/->query-context 
                                 % 
                                 :filters {:owner (get-owner %)} 
                                 :id id))] 
              {:deposit deposit})
  :handle-ok #(d/fetch-data (q/->query-context
                             %
                             :filters {:owner (get-owner %)} 
                             :id id)))

(defresource works-resource
  :malformed? (v/malformed? :facet-validator v/validate-work-facets
                            :filter-validator v/validate-work-filters
                            :deep-pagable true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(work/fetch (q/->query-context %)))

(defresource work-resource [doi]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? (->1 #(when-let [work (-> doi
                                     (doi-id/to-long-doi-uri)
                                     (work/fetch-one))]
                   {:work work}))
  :handle-ok :work)

(defresource work-health-resource [doi]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(work/fetch-quality doi)))

(defresource work-agency-resource [doi]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? (->1 #(when-let [agency (work/get-agency doi)] {:agency agency}))
  :handle-ok #(work/->agency-response doi (:agency %)))

(defn force-exact-request-doi
  "Why? DOIs are case insensitive. CrossRef APIs try to always present DOIs
   lowercases, but out in the real world they may appear mixed-case. We want
   clients to be given RDF that describes the case-sensitive URI they requested,
   so we avoid the canonical lower-case DOI and present metadata for the DOI
   exactly as requested."
  [request doi]
  (assoc (get-in request [:work :message]) 
    :URL 
    (->> doi
         (URLDecoder/decode)
         (doi-id/extract-long-doi)
         (str "http://dx.doi.org/"))))

(defresource work-transform-resource [doi]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
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

(defresource explicit-work-transform-resource [doi content-type]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :media-type-available? (fn [_] (some #{content-type} t/work-transform))
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
                  :body (transform/->format {:media-type content-type
                                             :parameters {}}
                                            (force-exact-request-doi % doi))})))

(defresource funders-resource
  :malformed? (v/malformed? :filter-validator v/validate-funder-filters)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(funder/fetch (q/->query-context %)))

(defresource funder-resource [funder-id]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [f (funder/fetch-one 
                          (q/->query-context % :id (fr-id/id-to-doi-uri funder-id)))]
              {:funder f})
  :handle-ok :funder)

(defresource funder-works-resource [funder-id]
  :malformed? (v/malformed? :facet-validator v/validate-work-facets
                            :filter-validator v/validate-work-filters
                            :deep-pagable true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(funder/fetch-works (q/->query-context % :id (fr-id/id-to-doi-uri funder-id))))

(defresource prefix-resource [px]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [p (prefix/fetch-one
                          (q/->query-context % :id (prefix-id/to-prefix-uri px)))]
              {:publisher p})
  :handle-ok :publisher)

(defresource prefix-works-resource [px]
  :malformed? (v/malformed? :facet-validator v/validate-work-facets
                            :filter-validator v/validate-work-filters
                            :deep-pagable true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(prefix/fetch-works (q/->query-context % :id (prefix-id/to-prefix-uri px))))

(defresource members-resource
  :malformed? (v/malformed? :filter-validator v/validate-member-filters)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(member/fetch (q/->query-context %)))

(defresource member-resource [id]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [m (member/fetch-one
                          (q/->query-context % :id (member-id/to-member-id-uri id)))]
              {:member m})
  :handle-ok :member)

(defresource member-works-resource [id]
  :malformed? (v/malformed? :facet-validator v/validate-work-facets
                            :filter-validator v/validate-work-filters
                            :deep-pagable true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [m (member/fetch-one
                          (q/->query-context % :id (member-id/to-member-id-uri id)))]
              {:member m})
  :handle-ok #(member/fetch-works (q/->query-context % :id (member-id/to-member-id-uri id))))

(defresource journals-resource
  :malformed? (v/malformed?)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(journal/fetch (q/->query-context %)))

(defresource journal-resource [issn]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [j (journal/fetch-one
                          (q/->query-context % :id (issn-id/normalize-issn issn)))]
              {:journal j})
  :handle-ok :journal)

(defresource journal-works-resource [issn]
  :malformed? (v/malformed? :facet-validator v/validate-work-facets
                            :filter-validator v/validate-work-filters
                            :deep-pagable true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [j (journal/fetch-one
                          (q/->query-context % :id (issn-id/normalize-issn issn)))]
              {:journal j})
  :handle-ok #(journal/fetch-works (q/->query-context % :id (issn-id/normalize-issn issn))))

(defresource licenses-resource
  :malformed? (v/malformed?)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(license/fetch-all (q/->query-context %)))

(defresource types-resource
  :malformed? (v/malformed?)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok (->1 #(data-types/fetch-all)))

(defresource type-resource [id]
  :malformed? (v/malformed? :singleton true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :exists? #(when-let [t (data-types/fetch-one (q/->query-context % :id id))]
              {:data-type t})
  :handle-ok :data-type)

(defresource type-works-resource [id]
  :malformed? (v/malformed? :facet-validator v/validate-work-facets
                            :filter-validator v/validate-work-filters
                            :deep-pagable true)
  :handle-malformed :validation-result
  :allowed-methods [:get :options]
  :available-media-types t/json
  :handle-ok #(data-types/fetch-works (q/->query-context % :id id)))

(defresource reverse-lookup-resource
  :malformed? (v/malformed? :singleton true)
  :new? false
  :respond-with-entity? true
  :handle-malformed :validation-result
  :allowed-methods [:post :options]
  :available-media-types t/json
  :handle-ok #(work/fetch-reverse {:terms (-> (get-in % [:request :body])
                                              (.bytes)
                                              slurp)}))

(defroutes restricted-api-routes
  (ANY "/deposits" {body :body}
       (deposits-resource body))
  (ANY "/deposits/:id" [id]
       (deposit-resource id))
  (ANY "/deposits/:id/data" [id]
       (deposit-data-resource id)))

(defroutes api-routes
  (ANY "/reverse" []
       reverse-lookup-resource)
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
  (ANY "/journals" []
       journals-resource)
  (ANY "/journals/:issn" [issn]
       (journal-resource issn))
  (ANY "/journals/:issn/works" [issn]
       (journal-works-resource issn))
  (ANY "/prefixes" []
       "Not implemented.")
  (ANY "/prefixes/:prefix" [prefix]
       (prefix-resource prefix))
  (ANY "/prefixes/:prefix/works" [prefix]
       (prefix-works-resource prefix))
  (ANY "/works" []
       works-resource)
  (ANY "/works/*" {{doi :*} :params}
       (cond (.endsWith doi ".xml")
             (redirect (str 
                        "/works/"
                        (string/replace doi #".xml" "")
                        "/transform/application/vnd.crossref.unixsd+xml"))
             (.endsWith doi "/agency")
             (work-agency-resource (string/replace doi #"/agency\z" ""))
             (.endsWith doi "/quality")
             (work-health-resource (string/replace doi #"/quality\z" ""))
             (.endsWith doi "/transform")
             (work-transform-resource (string/replace doi #"/transform\z" ""))
             (re-matches #".*/transform/.+\z" doi)
             (explicit-work-transform-resource
              (string/replace doi #"/transform/[^/]+/[^/]+\z" "")
              (second (re-matches #".*/transform/(.+)\z" doi)))
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
       (core-resource name)))
