(ns cayenne.api.v1.doc
  (:require [cayenne.api.v1.schema :as sc]
            [cayenne.api.v1.filter :refer [std-filters]]
            [compojure.core :refer [defroutes GET]]
            [clojure.data.json :as json]
            [clojure.java.io :refer [resource]]
            [ring.swagger.swagger-ui :refer [swagger-ui]]
            [ring.swagger.swagger2 :as rs]
            [schema.core :as s]))

(def info
  {:info 
   {:version "0.1"
    :title "Crossref Unified Resource API"
    :description (slurp (resource "description.md"))
    :termsOfService "https://github.com/CrossRef/rest-api-doc"
    :contact {:name "Crossref"
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
     :description "Endpoints that expose prefix related data"}
    {:name "member"
     :description "Endpoints that expose member related data"}
    {:name "type"
     :description "Endpoints that expose type related data"}]})

(defn- fields [field]
  (let [c-fields (get cayenne.api.v1.filter/compound-fields (keyword field))
        field-prefix (str "\n  + " field ".")]
    [field 
     (if c-fields
       (str field "." (clojure.string/join field-prefix c-fields)))]))
   
(defn- filters-description [title filters]
  (->> (map (comp fields key) filters)
       (map #(str "\n+ " (first %) (if (second %) (str "\n  + " (second %)))))
       clojure.string/join
       (str title 
            "\n ## Filters " 
            "\n Filters allow you to narrow queries. All filter results are lists."
            "\n This endpoint supports the following filters.")))

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
   {:get {:description (filters-description 
                         "Gets a collection of works for funder {id}."
                         std-filters)
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
   {:get {:description (filters-description 
                         "Gets a collection of works for issn {issn}."
                         std-filters)
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["journal"]}}})

(def works
  {"/works" 
   {:get {:description (filters-description 
                         "Gets a collection of works."
                         std-filters)
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
   {:get {:description (filters-description 
                         "Gets a collection of works with prefix {prefix}."
                         std-filters)
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
          :tags ["member"]}}
   "/members/:id" 
   {:get {:description "Gets a specific member by it's id, as an example use id 324"
          :parameters {:path {:id s/Int}}
          :responses {200 {:schema sc/MemberMessage
                           :description "The prefix data identified by {id}."}
                      404 {:description "The prefix data identified by {id} does not exist."}}
          :tags ["member"]}}
   "/members/:id/works"
   {:get {:description (filters-description 
                         "Gets a collection of works for member id {id}."
                         std-filters)
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["member"]}}})

(def types
  {"/types"
   {:get {:description "Gets a collection of types"
          :parameters sc/QueryParams
          :responses {200 {:schema sc/TypesMessage
                           :description "A collection of types"}}
          :tags ["type"]}}
   "/types/:id" 
   {:get {:description "Gets a specific type by it's id, as an example use `monograph`"
          :parameters {:path {:id s/Int}}
          :responses {200 {:schema sc/TypeMessage
                           :description "The type identified by {id}."}
                      404 {:description "The type identified by {id} does not exist."}}
          :tags ["type"]}}
   "/types/:id/works"
   {:get {:description (filters-description 
                         "Gets a collection of works for type id {id}."
                         std-filters)
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["type"]}}})

(def paths
  {:paths 
   (merge 
     funders 
     journals
     works
     prefixes
     members
     types)})

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
