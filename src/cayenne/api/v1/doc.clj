(ns cayenne.api.v1.doc
  (:require [compojure.core :refer [defroutes GET]]
            [clojure.data.json :as json]))

(def docs
  {:apiVersion 0.1
   :swaggerVersion 1.2
   :apis [{:path "/works"}
          {:path "/funders"}
          {:path "/publishers"}]
   :info {:title "CrossRef Unified Resource API"
          :contact "labs@crossref.org"
          :license "CC0 1.0 Universal"
          :licenseUrl "http://creativecommons.org/publicdomain/zero/1.0"}})

(defn api-path [path]
  {:path path
   :operations [{:method :GET
                 :responseMessages {:code 404
                                    :message "Entity not found."}}]})

(def works-docs
  {:apiVersion 01
   :swaggerVersion 1.2
   :resourcePath "/works"
   :apis [(api-path "/works")
          (api-path "/works/{doi}")
          (api-path "/works/random/{count}")]})

(def funders-docs
  {:apiVersion 01
   :swaggerVersion 1.2
   :resourcePath "/funders"
   :apis [(api-path "/funders")
          (api-path "/funders/{funderId}")
          (api-path "/funders/{funderId}/works")]})

(def publishers-docs
  {:apiVersion 01
   :swaggerVersion 1.2
   :resourcePath "/publishers"
   :apis [(api-path "/publishers")
          (api-path "/publishers/{ownerPrefix}")
          (api-path "/publishers/{ownerPrefix}/works")]})

(defroutes api-doc-routes
  (GET "/api-docs" [] (json/write-str docs))
  (GET "/api-docs/works" [] (json/write-str works-docs))
  (GET "/api-docs/funders" [] (json/write-str funders-docs))
  (GET "/api-docs/publishers" [] (json/write-str publishers-docs)))
