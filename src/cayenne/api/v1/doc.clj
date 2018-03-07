(ns cayenne.api.v1.doc
  (:require [cayenne.api.v1.schema :as sc]
            [compojure.core :refer [defroutes GET]]
            [clojure.data.json :as json]
            [ring.swagger.swagger-ui :refer [swagger-ui]]
            [ring.swagger.swagger2 :as rs]
            [schema.core :as s]))

(defroutes api-doc-routes
  (swagger-ui
    {:path "/swagger-ui"
     :swagger-docs "/swagger-docs"})
  (GET "/swagger-docs" [] 
       (json/write-str 
         (s/with-fn-validation
           (rs/swagger-json
             {:info {:version "0.1"
                     :title "Crossref Unified Resource API"
                     :description "Crossref Unified Resource API"
                     :termsOfService "https://github.com/CrossRef/rest-api-doc"
                     :contact {:name "Crossref Labs"
                               :email "support@crossref.org"
                               :url "https://crossref.org"}}
              :tags [{:name "funder"
                      :description "Endpoints that expose funder related data"}
                     {:name "journal"
                      :description "Endpoints that expose journal related data"}
                     {:name "work"
                      :description "Endpoints that expose works related data"}]
              :paths {"/funders" {:get {:description "Gets a collection of funders"
                                        :parameters (merge-with merge sc/FundersFilter sc/QueryParams)
                                        :responses {200 {:schema sc/FundersMessage
                                                         :description "A list of funders."}}
                                        :tags ["funder"]}}
                      "/funders/:id" {:get {:description "Gets a specific funder by it's id, as an example use id 501100006004"
                                            :parameters {:path {:id sc/FunderId}}
                                            :responses {200 {:schema sc/FunderMessage
                                                             :description "The funder identified by {id}."}
                                                        404 {:description "The funder identified by {id} does not exist."}}
                                            :tags ["funder"]}}
                      "/journals" {:get {:description "Gets a collection of journals"
                                         :parameters sc/QueryParams
                                         :responses {200 {:schema sc/JournalsMessage
                                                          :description "A list of journals"}}
                                         :tags ["journal"]}}
                      "/journals/:issn" {:get {:description "Gets a specific journal by it's issn, as an example use id 03064530"
                                               :parameters {:path {:id sc/JournalIssn}}
                                               :responses {200 {:schema sc/JournalMessage
                                                                :description "The journal identified by {issn}."}
                                                           404 {:description "The journal identified by {issn} does not exist."}}
                                               :tags ["journal"]}}
                      "/works" {:get {:description "Gets a collection of works"
                                         :parameters sc/QueryParams
                                         :responses {200 {:schema sc/WorksMessage
                                                          :description "A list of works"}}
                                         :tags ["work"]}}
                      "/works/:doi" {:get {:description "Gets a specific work by it's DOI, as an example use DOI 10.5555/12345678"
                                               :parameters {:path {:doi sc/WorkDoi}}
                                               :responses {200 {:schema sc/WorkMessage
                                                                :description "The work identified by {doi}."}
                                                           404 {:description "The work identified by {doi} does not exist."}}
                                               :tags ["work"]}}
                      "/works/:doi/agency" {:get {:description "Gets the agency associated with a specific work by it's DOI, as an example use DOI 10.5555/12345678"
                                               :parameters {:path {:doi sc/WorkDoi}}
                                               :responses {200 {:schema sc/AgencyMessage
                                                                :description "The agency associated with work identified by {doi}."}
                                                           404 {:description "The work identified by {doi} does not exist."}}
                                               :tags ["work"]}}
                      "/works/:doi/quality" {:get {:description "Gets the list of quality standards for work by it's DOI, as an example use DOI 10.5555/12345678"
                                               :parameters {:path {:doi sc/WorkDoi}}
                                               :responses {200 {:schema sc/QualityMessage
                                                                :description "The quality standards associated with work identified by {doi}."}
                                                           404 {:description "The work identified by {doi} does not exist."}}
                                               :tags ["work"]}}}})))))
