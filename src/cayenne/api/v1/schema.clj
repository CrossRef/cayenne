(ns cayenne.api.v1.schema
  (:require [ring.swagger.json-schema :refer [field]]
            [ring.swagger.swagger-ui :refer [swagger-ui]]
            [ring.swagger.swagger2 :as rs]
            [schema.core :as s]))

(s/defschema Query {:start-index s/Int :search-terms s/Str})

(s/defschema FunderId (field s/Str {:description "The id of the funder"}))
(s/defschema Funder {:id FunderId,
                     :location (field s/Str {:description "The geographic location of the funder"})
                     :name s/Str
                     :alt-names (field [s/Str] {:description "Other names this funder may be identified with"})
                     :uri s/Str
                     :replaces [s/Str]
                     :replaced-by [s/Str]
                     :tokens [s/Str]})

(s/defschema Message {:status s/Str
                      :message-type s/Str
                      :message-version s/Str})

(s/defschema Funders {:items-per-page s/Int
                      :query Query
                      :total-results s/Int
                      :items [Funder]})

(s/defschema FundersMessage (merge Message {:message Funders}))

(s/defschema 
  FundersFilter 
  {:query 
   {:filter (field s/Str {:description "Exposes the ability to search funders by location using a Lucene based syntax"
                          :required false
                          :pattern "location:.*"})}})

(s/defschema QueryParams 
  {:query {:rows (field s/Int {:description "The number of rows to return" :required false})
           :offset (field s/Int {:description "The number of rows to skip before returning" :required false})}})
