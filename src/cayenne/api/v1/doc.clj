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
                      :description "Endpoints that expose funder related data"}]
              :paths {"/funders" {:get {:description "Gets a collection of funders"
                                        :parameters (merge-with merge sc/FundersFilter sc/QueryParams)
                                        :responses {200 {:schema sc/FundersMessage
                                                         :description "A list of funders."}}
                                        :tags ["funder"]}}
                      "/funders/:id" {:get {:description "Gets a specific funder by it's id, as an example use id 501100006004"
                                            :parameters {:path {:id sc/FunderId}}
                                            :responses {200 {:schema sc/Funders
                                                             :description "The funder identified by {id}."}
                                                        404 {:description "The funder identified by {id} does not exist."}}
                                            :tags ["funder"]}}}})))))
