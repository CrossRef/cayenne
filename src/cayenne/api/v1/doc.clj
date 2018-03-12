(ns cayenne.api.v1.doc
  (:require [cayenne.api.v1.schema :as sc]
            [cayenne.api.v1.filter :refer [std-filters compound-fields]]
            [cayenne.api.v1.facet :refer [std-facets]]
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
   [{:name "Funder"
     :description "Endpoints that expose funder related data"}
    {:name "Journal"
     :description "Endpoints that expose journal related data"}
    {:name "Work"
     :description "Endpoints that expose works related data"}
    {:name "Prefix"
     :description "Endpoints that expose prefix related data"}
    {:name "Member"
     :description "Endpoints that expose member related data"}
    {:name "Type"
     :description "Endpoints that expose type related data"}]})

(defn- fields [compound-fields field]
  (let [c-fields (get compound-fields (keyword field))
        field-prefix (str "\n  + " field ".")]
    [field 
     (if c-fields
       (str field "." (clojure.string/join field-prefix c-fields)))]))

(defn- fields-description 
  ([title filters]
   (fields-description title filters {}))
  ([title filters compound-fields]
   (->> (map (comp (partial fields compound-fields) key) filters)
        (map #(str "\n+ " (first %) (if (second %) (str "\n  + " (second %)))))
        clojure.string/join
        (str title))))

(defn- filters-description [title]
  (fields-description 
    (str title 
         "\n ## Filters " 
         "\n Filters allow you to narrow queries. All filter results are lists."
         "\n This endpoint supports the following filters.")
    std-filters
    compound-fields))

(defn- facets-description [title]
  (fields-description 
    (str "" 
         "\n ## Facets " 
         "\n Facet counts can be retrieved by enabling faceting. Facets are enabled by providing facet field names along with a maximum number of returned term values. The larger the number of returned values, the longer the query will take. Some facet fields can accept a `*` as their maximum, which indicates that all values should be returned.Filters allow you to narrow queries. All filter results are lists." 
         "\n This endpoint supports the following facets")
    (reduce merge (map (comp #(assoc {} % []) :external-field val) std-facets))))

(defn- works-description [title]
  (str 
    (filters-description title)
    (facets-description "")))

(def funders
  {"/funders" 
   {:get {:description "Gets a collection of funders"
          :parameters (merge-with merge sc/FundersFilter sc/QueryParams)
          :responses {200 {:schema sc/FundersMessage
                           :description "A list of funders."}}
          :tags ["Funder"]}}
   "/funders/:id" 
   {:get {:description "Gets a specific funder by it's id, as an example use id 501100006004"
          :parameters {:path {:id sc/FunderId}}
          :responses {200 {:schema sc/FunderMessage
                           :description "The funder identified by {id}."}
                      404 {:description "The funder identified by {id} does not exist."}}
          :tags ["Funder"]}}
   "/funders/:id/works"
   {:get {:description (works-description "Gets a collection of works for funder {id}.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Funder"]}}})

(def journals
  {"/journals" 
   {:get {:description "Gets a collection of journals"
          :parameters sc/QueryParams
          :responses {200 {:schema sc/JournalsMessage
                           :description "A list of journals"}}
          :tags ["Journal"]}}
   "/journals/:issn" 
   {:get {:description "Gets a specific journal by it's issn, as an example use id 03064530"
          :parameters {:path {:id sc/JournalIssn}}
          :responses {200 {:schema sc/JournalMessage
                           :description "The journal identified by {issn}."}
                      404 {:description "The journal identified by {issn} does not exist."}}
          :tags ["Journal"]}}
   "/journals/:issn/works"
   {:get {:description (works-description "Gets a collection of works for issn {issn}.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Journal"]}}})

(def works
  {"/works" 
   {:get {:description (works-description "Gets a collection of works.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Work"]}}
   "/works/:doi" 
   {:get {:description "Gets a specific work by it's DOI, as an example use DOI 10.5555/12345678"
          :parameters {:path {:doi sc/WorkDoi}}
          :responses {200 {:schema sc/WorkMessage
                           :description "The work identified by {doi}."}
                      404 {:description "The work identified by {doi} does not exist."}}
          :tags ["Work"]}}
   "/works/:doi/agency" 
   {:get {:description "Gets the agency associated with a specific work by it's DOI, as an example use DOI 10.5555/12345678"
          :parameters {:path {:doi sc/WorkDoi}}
          :responses {200 {:schema sc/AgencyMessage
                           :description "The agency associated with work identified by {doi}."}
                      404 {:description "The work identified by {doi} does not exist."}}
          :tags ["Work"]}}
   "/works/:doi/quality" 
   {:get {:description "Gets the list of quality standards for work by it's DOI, as an example use DOI 10.5555/12345678"
          :parameters {:path {:doi sc/WorkDoi}}
          :responses {200 {:schema sc/QualityMessage
                           :description "The quality standards associated with work identified by {doi}."}
                      404 {:description "The work identified by {doi} does not exist."}}
          :tags ["Work"]}}})

(def prefixes
  {"/prefixes/:prefix" 
   {:get {:description "Gets a specific prefix by it's prefix, as an example use prefix 10.1016"
          :parameters {:path {:prefix s/Str}}
          :responses {200 {:schema sc/PrefixMessage
                           :description "The prefix data identified by {prefix}."}
                      404 {:description "The prefix data identified by {prefix} does not exist."}}
          :tags ["Prefix"]}}
   "/prefixes/:prefix/works"
   {:get {:description (works-description "Gets a collection of works with prefix {prefix}.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Prefix"]}}})

(def members
  {"/members"
   {:get {:description "Gets a collection of members"
          :parameters sc/QueryParams
          :responses {200 {:schema sc/MembersMessage
                           :description "A collection of members"}}
          :tags ["Member"]}}
   "/members/:id" 
   {:get {:description "Gets a specific member by it's id, as an example use id 324"
          :parameters {:path {:id s/Int}}
          :responses {200 {:schema sc/MemberMessage
                           :description "The prefix data identified by {id}."}
                      404 {:description "The prefix data identified by {id} does not exist."}}
          :tags ["Member"]}}
   "/members/:id/works"
   {:get {:description (works-description "Gets a collection of works for member id {id}.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Member"]}}})

(def types
  {"/types"
   {:get {:description "Gets a collection of types"
          :parameters sc/QueryParams
          :responses {200 {:schema sc/TypesMessage
                           :description "A collection of types"}}
          :tags ["Type"]}}
   "/types/:id" 
   {:get {:description "Gets a specific type by it's id, as an example use `monograph`"
          :parameters {:path {:id s/Int}}
          :responses {200 {:schema sc/TypeMessage
                           :description "The type identified by {id}."}
                      404 {:description "The type identified by {id} does not exist."}}
          :tags ["Type"]}}
   "/types/:id/works"
   {:get {:description (works-description "Gets a collection of works for type id {id}.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Type"]}}})

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
