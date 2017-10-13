(ns cayenne.elastic.mappings
  (:require [qbits.spandex :as elastic]))

(def contributor-properties
  {:contribution        {:type "string"}
   :given-name          {:type "string"}
   :family-name         {:type "string"}
   :org-name            {:type "string"}
   :prefix              {:type "string"}
   :suffix              {:type "string"}
   :orcid               {:type "string"}
   :affiliations        {:type "string"}
   :authenticated-orcid {:type "boolean"}})

(def issn-properties
  {:value {:type "string"}
   :kind  {:type "string"}})

(def isbn-properties
  {:value {:type "string"}
   :kind  {:type "string"}})

(def funder-properties)

(def update-properties)

(def clinical-trial-properties)

(def event-properties)

(def license-properties)

(def assertion-properties)

(def relation-properties)

(def reference-properties)

(def work-properties
  {:random          {:type "integer"}
   :kind            {:type "string"}
   :original-doi    {:type "string"}
   :normalised-doi  {:type "string"}
   :owner-prefix    {:type "string"}
   :member-id       {:type "integer"}
   :language-title  {:type "string"}
   :original-title  {:type "string"}
   :container-title {:type "string"}
   :issns           {:type "object" :properties issn-properties}
   :isbns           {:type "object" :properties isbn-properties}
   :contribtuors    {:type "object" :properties contributor-properties}
   :funders         {:type "object" :properties funder-properties}
   :awards          {:type "object" :properties award-properties}
   :updated-by      {:type "object" :properties update-properties}
   :updates         {:type "object" :properties update-properties}
   :clinical-trials {:type "object" :properties clinical-trial-properties}
   :events          {:type "object" :properties event-properties}
   :links           {:type "object" :properties link-properties}
   :licenses        {:type "object" :properties license-properties}
   :assertions      {:type "object" :properties assertion-properties}
   :relations       {:type "object" :properties relation-properties}
   :references      {:type "object" :properties reference-properties}})

(def member-properties
  {})

(def funder-properties
  {})

(def prefix-properties
  {})

(def subject-properties
  {})

(def journal-properties
  {})

(def mapping-types
  {"work"    {:properties work-properties}
   "member"  {:properties member-properties}
   "funder"  {:properties funder-properties}
   "prefix"  {:properties prefix-properties}
   "subject" {:properties subject-properties}
   "journal" {:properties journal-properties}})
  
(defn create-mappings [conn]
  (elastic/request conn
                   :url "cayenne"
                   :method :put
                   :body {:mappings mapping-types}))
