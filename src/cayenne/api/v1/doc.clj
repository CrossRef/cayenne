(ns cayenne.api.v1.doc
  (:require [cayenne.api.v1.schema :as sc]
            [compojure.core :refer [defroutes GET]]
            [clojure.data.json :as json]
            [ring.swagger.swagger-ui :refer [swagger-ui]]
            [ring.swagger.swagger2 :as rs]
            [schema.core :as s]))

(def info
  {:info 
   {:version "0.1"
    :title "Crossref Unified Resource API"
    :description "Crossref Unified Resource API"
    :termsOfService "https://github.com/CrossRef/rest-api-doc"
    :contact {:name "Crossref Labs"
              :email "support@crossref.org"
              :url "https://crossref.org"}}})

(def tags
  {:tags
   [{:name "funder"
     :description "Endpoints that expose funder related data"}
    {:name "journal"
     :description "Endpoints that expose journal related data"}
    {:name "work"
     :description "Endpoints that expose works related data"}
    {:name "prefix"
     :description "Endpoints that expose prefix related data"}]})

(def funders
  {"/funders" 
   {:get {:description "Gets a collection of funders"
          :parameters (merge-with merge sc/FundersFilter sc/QueryParams)
          :responses {200 {:schema sc/FundersMessage
                           :description "A list of funders."}}
          :tags ["funder"]}}
   "/funders/:id" 
   {:get {:description "Gets a specific funder by it's id, as an example use id 501100006004"
          :parameters {:path {:id sc/FunderId}}
          :responses {200 {:schema sc/FunderMessage
                           :description "The funder identified by {id}."}
                      404 {:description "The funder identified by {id} does not exist."}}
          :tags ["funder"]}}
   "/funders/:id/works"
   {:get {:description "Gets a collection of works for funder {id}"
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["funder"]}}})

(def journals
  {"/journals" 
   {:get {:description "Gets a collection of journals"
          :parameters sc/QueryParams
          :responses {200 {:schema sc/JournalsMessage
                           :description "A list of journals"}}
          :tags ["journal"]}}
   "/journals/:issn" 
   {:get {:description "Gets a specific journal by it's issn, as an example use id 03064530"
          :parameters {:path {:id sc/JournalIssn}}
          :responses {200 {:schema sc/JournalMessage
                           :description "The journal identified by {issn}."}
                      404 {:description "The journal identified by {issn} does not exist."}}
          :tags ["journal"]}}
   "/journals/:issn/works"
   {:get {:description "Gets a collection of works for issn {issn}"
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["journal"]}}})

(def works
  {"/works" 
   {:get {:description "Gets a collection of works"
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["work"]}}
   "/works/:doi" 
   {:get {:description "Gets a specific work by it's DOI, as an example use DOI 10.5555/12345678"
          :parameters {:path {:doi sc/WorkDoi}}
          :responses {200 {:schema sc/WorkMessage
                           :description "The work identified by {doi}."}
                      404 {:description "The work identified by {doi} does not exist."}}
          :tags ["work"]}}
   "/works/:doi/agency" 
   {:get {:description "Gets the agency associated with a specific work by it's DOI, as an example use DOI 10.5555/12345678"
          :parameters {:path {:doi sc/WorkDoi}}
          :responses {200 {:schema sc/AgencyMessage
                           :description "The agency associated with work identified by {doi}."}
                      404 {:description "The work identified by {doi} does not exist."}}
          :tags ["work"]}}
   "/works/:doi/quality" 
   {:get {:description "Gets the list of quality standards for work by it's DOI, as an example use DOI 10.5555/12345678"
          :parameters {:path {:doi sc/WorkDoi}}
          :responses {200 {:schema sc/QualityMessage
                           :description "The quality standards associated with work identified by {doi}."}
                      404 {:description "The work identified by {doi} does not exist."}}
          :tags ["work"]}}})

(def prefixes
  {"/prefixes/:prefix" 
   {:get {:description "Gets a specific prefix by it's prefix, as an example use prefix 10.1016"
          :parameters {:path {:prefix s/Str}}
          :responses {200 {:schema sc/PrefixMessage
                           :description "The prefix data identified by {prefix}."}
                      404 {:description "The prefix data identified by {prefix} does not exist."}}
          :tags ["prefix"]}}
   "/prefixes/:prefix/works"
   {:get {:description "Gets a collection of works with prefix {prefix}"
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["prefix"]}}})

(def members
  {"/members"
   {:get {:description "Gets a collection of members"
          :parameters sc/QueryParams
          :responses {200 {:schema sc/MembersMessage
                           :description "A collection of members"}}
          :tags ["members"]}}
   "/members/:id" 
   {:get {:description "Gets a specific member by it's id, as an example use prefix 324"
          :parameters {:path {:id s/Int}}
          :responses {200 {:schema sc/MemberMessage
                           :description "The prefix data identified by {prefix}."}
                      404 {:description "The prefix data identified by {prefix} does not exist."}}
          :tags ["members"]}}
   "/members/:id/works"
   {:get {:description "Gets a collection of works for member prefix {id}"
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["members"]}}})

(def paths
  {:paths 
   (merge 
     funders 
     journals
     works
     prefixes
     members)})

(defroutes api-doc-routes
  (swagger-ui
    {:path "/swagger-ui"
     :swagger-docs "/swagger-docs"})
  (GET "/swagger-docs" [] 
       (json/write-str 
         (s/with-fn-validation
           (rs/swagger-json
             (merge
               info
               tags
               paths))))))
