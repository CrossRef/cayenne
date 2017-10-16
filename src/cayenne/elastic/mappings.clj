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

(def funder-properties
  {:funder-name {:type "string"}
   :funder-doi  {:type "string"}
   :awards      {:type "string"}})

(def update-properties)

(def clinical-trial-properties)

(def event-properties)

(def license-properties)

(def assertion-properties
  {:name            {:type "string"}
   :label           {:type "string"}
   :group-name      {:type "string"}
   :group-label     {:type "string"}
   :url             {:type "string"}
   :value           {:type "string"}
   :order           {:type "integer"}
   :explanation-url {:type "string"}})

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
   :issns           {:type "nested" :properties issn-properties}
   :isbns           {:type "nested" :properties isbn-properties}
   :contribtuors    {:type "nested" :properties contributor-properties}
   :funders         {:type "nested" :properties funder-properties}
   :awards          {:type "nested" :properties award-properties}
   :updated-by      {:type "nested" :properties update-properties}
   :updates         {:type "nested" :properties update-properties}
   :clinical-trials {:type "nested" :properties clinical-trial-properties}
   :events          {:type "nested" :properties event-properties}
   :links           {:type "nested" :properties link-properties}
   :licenses        {:type "nested" :properties license-properties}
   :assertions      {:type "nested" :properties assertion-properties}
   :relations       {:type "nested" :properties relation-properties}
   :references      {:type "nested" :properties reference-properties}})

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
  
(defn create-indexes
  "Creates an index per top-level document type - in preparation for ES 6+
   compatibility (which will remove multiple types per field name, making
   multiple types per index unworkable."
  [conn]
  (doseq [[index-name index-data] mapping-types]
    (elastic/request conn
                     :url index-name
                     :method :put
                     :body {:mappings {"doc" index-data}})))
