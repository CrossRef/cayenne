(ns cayenne.api.v1.schema
  (:require [ring.swagger.json-schema :refer [field]]
            [ring.swagger.swagger-ui :refer [swagger-ui]]
            [ring.swagger.swagger2 :as rs]
            [schema.core :as s]))

;; Generic
(s/defschema Query
  {:start-index s/Int :search-terms s/Str})

(s/defschema DateParts
  {:date-parts [[s/Int s/Int s/Int]]})

(s/defschema Date
  (merge DateParts {:date-time s/Inst
                    :timestamp s/Int}))

(s/defschema Message
  {:status s/Str
   :message-type s/Str
   :message-version s/Str})

(s/defschema QueryParams
  {:query {(s/optional-key :rows) (field s/Int {:description "The number of rows to return"})
           :mailto (field s/Str 
                          {:pattern #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z]{2,6}$" 
                           :description "The email address to identify yourself and be in the \"polite pool\", see [https://github.com/CrossRef/rest-api-doc#etiquette](https://github.com/CrossRef/rest-api-doc#etiquette)"})
           (s/optional-key :offset) (field s/Int {:description "The number of rows to skip before returning"})}})

(s/defschema IdAndLabel
  {:id s/Str :label s/Str})

(s/defschema Author
  {:ORCID s/Str
   :authenticated-orcid Boolean
   :given s/Str
   :family s/Str
   :sequence s/Str
   :affiliation [s/Str]})

(s/defschema Coverage
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

(s/defschema Flags
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

(s/defschema DoiCounts
  {:total-dois s/Int
   :current-dois s/Int
   :backfile-dois s/Int})

;; Funders
(s/defschema FunderId (field s/Str {:description "The id of the funder"}))
(s/defschema Funder {:id FunderId
                     :location (field s/Str {:description "The geographic location of the funder"})
                     :name s/Str
                     :alt-names (field [s/Str] {:description "Other names this funder may be identified with"})
                     :uri s/Str
                     :replaces [s/Str]
                     :replaced-by [s/Str]
                     :tokens [s/Str]})

(s/defschema FunderMessage
  (merge Message {:message-type #"funder" :message Funder}))

(s/defschema Funders
  {:items-per-page s/Int
   :query Query
   :total-results s/Int
   :items [Funder]})

(s/defschema FundersMessage
  (merge Message {:message-type #"funder-list"
                  :message Funders}))

(s/defschema
  FundersFilter
  {:query
   {(s/optional-key :filter) 
    (field s/Str {:description "Exposes the ability to search funders by location using a Lucene based syntax"
                  :pattern "location:.*"})}})

;; Journals
(s/defschema JournalIssn (field [s/Str] {:description "The ISSN identifiers associated with the journal"}))
(s/defschema JournalIssnType
  {:value s/Str
   :type s/Str})

(s/defschema Journal
  {:title (field s/Str {:description "The title of the journal"})
   :publisher (field s/Str {:description "The publisher of the journal"})
   :last-status-check-time s/Int
   :counts DoiCounts
   :dois-by-issued-year [[s/Int s/Int]]
   :coverage Coverage
   :flags Flags
   :subjects [s/Str]
   :issn-type JournalIssnType
   :ISSN JournalIssn})

(s/defschema JournalMessage
  (merge Message {:message-type #"journal" :message Journal}))

(s/defschema Journals
  {:items-per-page s/Int
   :query Query
   :total-results s/Int
   :items [Journal]})

(s/defschema JournalsMessage
  (merge Message {:message-type #"journal-list"
                  :message Journals}))

;; works
(s/defschema
  WorksQuery
  {:query 
   {(s/optional-key :select) 
    (field s/Str {:description "Exposes the ability to select certain fields, supports a comma separated list of fields, e.g. `DOI,volume`"
                  :pattern #"^\w+(,\w+)*$"})
    (s/optional-key :filter) 
    (field s/Str {:description "Exposes the ability to filter by certain fields, supports a comma separated list of luncene filters, e.g. `content-domain:psychoceramics.labs.crossref.org`"})
    (s/optional-key :facets) 
    (field s/Str {:description "Exposes the ability to retrieve counts for pre-defined facets e.g. `type-name:*` returns counts of all works by type"})
    (s/optional-key :query) 
    (field s/Str {:description "Exposes the ability to free text query certain fields, supports a comma separated list of luncene filters, e.g. `title:cortisol`"})
    (s/optional-key :cursor) 
    (field s/Str {:description "Exposes the ability to deep page through large result sets, where offset would cause performance problems"})
    (s/optional-key :sample) 
    (field s/Int {:description "Exposes the ability to return `N` number of random sample items"})
    (s/optional-key :sort) 
    (field s/Str {:description "Exposes the ability to sort results by a certain field, e.g `score`"})
    (s/optional-key :order) 
    (field s/Str {:description "Combined with sort can be used to specify the order of results, e.g. asc or desc"
                  :pattern #"(asc|desc)"})}})

(s/defschema Agency IdAndLabel)
(s/defschema Quality {:id s/Str :description s/Str :pass Boolean})
(s/defschema WorkDoi (field [s/Str] {:description "The DOI identifier associated with the work"}))
(s/defschema WorkLink
  {:URL s/Str
   :content-type s/Str
   :content-version s/Str
   :intended-application s/Str})

(s/defschema WorkLicense
  {:URL s/Str
   :start Date
   :delay-in-days s/Int
   :content-version s/Str})

(s/defschema WorkDomain
  {:domain [s/Str]
    :crossmark-restriction Boolean})

(s/defschema WorkReview
  {:type s/Str
   :running-number s/Str
   :revision-round s/Str
   :stage s/Str
   :competing-interest-statement s/Str
   :recommendation s/Str
   :language s/Str})

(s/defschema WorkInstitution
  {:name s/Str
   :place [s/Str]
   :department [s/Str]
   :accronym [s/Str]})

(s/defschema Work
  {:indexed Date
   (s/optional-key :institution) WorkInstitution
   :reference-count s/Int
   :publisher s/Str
   :issue s/Str
   :content-domain WorkDomain
   :short-container-title s/Str
   :published-print DateParts
   :DOI WorkDoi
   :type s/Str
   :created Date
   :license [WorkLicense]
   :page s/Str
   :source s/Str
   :is-reference-by-count s/Int
   :title [s/Str]
   (s/optional-key :original-title) [s/Str]
   :short-title [s/Str]
   :prefix s/Str
   :volume s/Str
   :member s/Str
   :container-title [s/Str]
   :link [WorkLink]
   :deposited Date
   :score Long
   :author [Author]
   :URL s/Str
   :references-count s/Int
   (s/optional-key :review) WorkReview})

(s/defschema WorkMessage
  (merge Message
         {:message-type #"work" 
          :message Work}))

(s/defschema Works
  {:items-per-page s/Int
   :query Query
   :total-results s/Int
   (s/optional-key :next-cursor) (field s/Str {:description "Used to navigate to the next page of results when using cursor deep paging"})
   :items [Work]})

(s/defschema WorksMessage
  (merge Message
         {:message-type #"work-list"
          :message Works}))

(s/defschema DoiAgency
  {:DOI WorkDoi
   :agency Agency})

(s/defschema AgencyMessage
  (merge Message {:message-type #"work-agency" :message DoiAgency}))

(s/defschema QualityMessage
  (merge Message {:message-type #"work-quality" :message [Quality]}))

;; Prefixes
(s/defschema Prefix
  {:member s/Str
   :name s/Str
   (s/optional-key :value) s/Str
   (s/optional-key :public-reference) Boolean
   (s/optional-key :reference-visibilty) s/Str
   :prefix s/Str})

(s/defschema PrefixMessage
  (merge Message {:message-type #"prefix" :message Prefix}))

;; Members
(s/defschema Member
  {:id s/Int
   :primary-name s/Str
   :last-status-check-time s/Int
   :counts DoiCounts
   :dois-by-issued-year [[s/Int s/Int]]
   :prefixes [s/Str]
   :coverage Coverage
   :flags Flags
   :tokens [s/Str]
   :names [s/Str]
   :location s/Str
   :prefix [Prefix]})

(s/defschema Members
  {:items-per-page s/Int
   :query Query
   :total-results s/Int
   :items [Member]})

(s/defschema MembersMessage
  (merge Message
         {:message-type #"member-list"
          :message Members}))

(s/defschema MemberMessage
  (merge Message
         {:message-type #"member" 
          :message Member}))

;; Types
(s/defschema Type IdAndLabel)

(s/defschema Types
  {:items-per-page s/Int
   :query Query
   :total-results s/Int
   :items [Type]})

(s/defschema TypesMessage
  (merge Message
         {:message-type #"type-list"
          :message Types}))

(s/defschema TypeMessage
  (merge Message
         {:message-type #"type" 
          :message Type}))
