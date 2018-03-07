(ns cayenne.api.v1.schema
  (:require [ring.swagger.json-schema :refer [field]]
            [ring.swagger.swagger-ui :refer [swagger-ui]]
            [ring.swagger.swagger2 :as rs]
            [schema.core :as s]))

;; Generic
(s/defschema Query 
  {:start-index s/Int :search-terms s/Str})

(s/defschema Message 
  {:status s/Str
   :message-type s/Str
   :message-version s/Str})

(s/defschema QueryParams 
  {:query {:rows (field s/Int {:description "The number of rows to return" :required false})
           :offset (field s/Int {:description "The number of rows to skip before returning" :required false})}})

;; Funders
(s/defschema FunderId (field s/Str {:description "The id of the funder"}))
(s/defschema Funder {:id FunderId,
                     :location (field s/Str {:description "The geographic location of the funder"})
                     :name s/Str
                     :alt-names (field [s/Str] {:description "Other names this funder may be identified with"})
                     :uri s/Str
                     :replaces [s/Str]
                     :replaced-by [s/Str]
                     :tokens [s/Str]})

(s/defschema FunderMessage (merge Message {:message Funder}))
(s/defschema Funders (merge Message {:message {:items-per-page s/Int
                                               :query Query
                                               :total-results s/Int
                                               :items [Funder]}}))

(s/defschema 
  FundersFilter 
  {:query 
   {:filter (field s/Str {:description "Exposes the ability to search funders by location using a Lucene based syntax"
                          :required false
                          :pattern "location:.*"})}})

;; Journals
(s/defschema JournalIssn (field [s/Str] {:description "The ISSN identifiers associated with the journal"}))
(s/defschema JournalCoverage 
  {:affiliations-current s/Int
   :funders-backfile s/Int
   :licenses-backfile s/Int
   :funders-current s/Int
   :affiliations-backfile s/Int
   :resource-links-backfile s/Int
   :orcids-backfile s/Int
   :update-policies-current s/Int
   :orcids-current s/Int
   :references-backfie s/Int
   :award-numbers-backfile s/Int
   :update-policies-backfile s/Int
   :licenses-current s/Int
   :award-numbers-current s/Int
   :abstracts-backfile s/Int
   :resource-links-current s/Int
   :abstracts-current s/Int
   :references-current s/Int})

(s/defschema JournalFlags
  {:deposits-abstracts-current Boolean
   :deposits-orcids-current Boolean
   :deposits Boolean
   :deposits-affiliations-backfile Boolean
   :deposits-update-policies-backfile Boolean
   :deposits-award-numbers-current Boolean
   :deposits-resource-links-current Boolean
   :deposits-articles Boolean
   :deposits-affiliations-current Boolean
   :deposits-funders-current Boolean
   :deposits-references-backfile Boolean
   :deposits-abstracts-backfile Boolean
   :deposits-licenses-backfile Boolean
   :deposits-award-numbers-backfile Boolean
   :deposits-references-current Boolean
   :deposits-resource-links-backfile Boolean
   :deposits-orcids-backfile Boolean
   :deposits-funders-backfile Boolean
   :deposits-update-policies-current Boolean
   :deposits-licenses-current Boolean})

(s/defschema JournalCounts
  {:total-dois s/Int
   :current-dois s/Int
   :backfile-dois s/Int})

(s/defschema JournalIssnType 
  {:value s/Str
   :type s/Str})

(s/defschema Journal 
  {:title (field s/Str {:description "The title of the journal"}) ,
   :publisher (field s/Str {:description "The publisher of the journal"})
   :last-status-check-time s/Int
   :counts JournalCounts
   :dois-by-issued-year [[s/Int s/Int]]
   :coverage JournalCoverage
   :flags JournalFlags
   :subjects [s/Str]
   :issn-type JournalIssnType
   :ISSN JournalIssn})

(s/defschema JournalMessage
  (merge Message {:message Journal}))

(s/defschema Journals (merge Message {:message {:items-per-page s/Int
                                                :query Query
                                                :total-results s/Int
                                                :items [Journal]}}))
