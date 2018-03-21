(ns cayenne.api.v1.doc
  (:require [cayenne.api.v1.schema :as sc]
            [cayenne.api.v1.filter :refer [std-filters compound-fields]]
            [cayenne.api.v1.fields :refer [work-fields]]
            [cayenne.api.v1.facet :refer [std-facets]]
            [cayenne.api.v1.query :refer [select-fields sort-fields]]
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
     (when c-fields
       (str field "." (clojure.string/join field-prefix c-fields)))]))

(defn- fields-description
  ([title filters]
   (fields-description title filters {}))
  ([title filters compound-fields]
   (->> (map (comp (partial fields compound-fields) key) filters)
        (map #(str "\n+ " (first %) (when (second %) (str "\n  + " (second %)))))
        clojure.string/join
        (str title))))

(defn- filters-description []
  (fields-description
    (slurp (resource "filters-description.md"))
    std-filters
    compound-fields))

(defn- facets-description []
  (fields-description
    (slurp (resource "facets-description.md"))
    (reduce merge (map (comp #(assoc {} % []) :external-field val) std-facets))))

(defn- selects-description []
  (fields-description
    (slurp (resource "selects-description.md"))
    select-fields))

(defn- sorts-description []
  (fields-description
    (slurp (resource "sorts-description.md"))
    sort-fields))

(defn- query-description []
  (fields-description
    (slurp (resource "query-description.md"))
    work-fields))

(defn- works-description [title]
  (str
    title
    (filters-description)
    (query-description)
    (facets-description)
    (selects-description)
    (sorts-description)))

(def funders
  {"/funders"
   {:get {:description "Returns a list of all funders in the [Funder Registry](https://github.com/Crossref/open-funder-registry)."
          :parameters (merge-with merge sc/FundersFilter sc/QueryParams)
          :responses {200 {:schema sc/FundersMessage
                           :description "A list of funders."}}
          :tags ["Funder"]}}
   "/funders/:id"
   {:get {:description "Returns metadata for specified funder **and** its suborganizations, as an example use id 501100006004"
          :parameters {:path {:id sc/FunderId}}
          :responses {200 {:schema sc/FunderMessage
                           :description "The funder identified by {id}."}
                      404 {:description "The funder identified by {id} does not exist."}}
          :tags ["Funder"]}}
   "/funders/:id/works"
   {:get {:description (works-description "Returns list of works associated with the specified {id}.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Funder"]}}})

(def journals
  {"/journals"
   {:get {:description "Return a list of journals in the Crossref database."
          :parameters sc/QueryParams
          :responses {200 {:schema sc/JournalsMessage
                           :description "A list of journals"}}
          :tags ["Journal"]}}
   "/journals/:issn"
   {:get {:description "Returns information about a journal with the given ISSN, as an example use ISSN 03064530"
          :parameters {:path {:id sc/JournalIssn}}
          :responses {200 {:schema sc/JournalMessage
                           :description "The journal identified by {issn}."}
                      404 {:description "The journal identified by {issn} does not exist."}}
          :tags ["Journal"]}}
   "/journals/:issn/works"
   {:get {:description (works-description "Returns a list of works in the journal identified by {issn}.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Journal"]}}})

(def works
  {"/works"
   {:get {:description (works-description "Returns a list of all works (journal articles, conference proceedings, books, components, etc), 20 per page.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Work"]}}
   "/works/:doi"
   {:get {:description "Returns metadata for the specified Crossref DOI, as an example use DOI 10.5555/12345678"
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
   {:get {:description "Returns metadata for the DOI owner prefix, as an example use prefix 10.1016"
          :parameters {:path {:prefix s/Str}}
          :responses {200 {:schema sc/PrefixMessage
                           :description "The prefix data identified by {prefix}."}
                      404 {:description "The prefix data identified by {prefix} does not exist."}}
          :tags ["Prefix"]}}
   "/prefixes/:prefix/works"
   {:get {:description (works-description "Returns list of works associated with specified {prefix}.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Prefix"]}}})

(def members
  {"/members"
   {:get {:description "Returns a list of all Crossref members (mostly publishers)."
          :parameters sc/QueryParams
          :responses {200 {:schema sc/MembersMessage
                           :description "A collection of members"}}
          :tags ["Member"]}}
   "/members/:id"
   {:get {:description "Returns metadata for a Crossref member, as an example use id 324"
          :parameters {:path {:id s/Int}}
          :responses {200 {:schema sc/MemberMessage
                           :description "The prefix data identified by {id}."}
                      404 {:description "The prefix data identified by {id} does not exist."}}
          :tags ["Member"]}}
   "/members/:id/works"
   {:get {:description (works-description "Returns list of works associated with a Crossref member (deposited by a Crossref member) with {id}.")
          :parameters (merge-with merge sc/WorksQuery sc/QueryParams)
          :responses {200 {:schema sc/WorksMessage
                           :description "A list of works"}}
          :tags ["Member"]}}})

(def types
  {"/types"
   {:get {:description "Returns a list of valid work types."
          :parameters sc/QueryParams
          :responses {200 {:schema sc/TypesMessage
                           :description "A collection of types"}}
          :tags ["Type"]}}
   "/types/:id"
   {:get {:description "Returns information about a metadata work type, as an example use `monograph`"
          :parameters {:path {:id s/Int}}
          :responses {200 {:schema sc/TypeMessage
                           :description "The type identified by {id}."}
                      404 {:description "The type identified by {id} does not exist."}}
          :tags ["Type"]}}
   "/types/:id/works"
   {:get {:description (works-description "returns list of works of type {id}.")
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
