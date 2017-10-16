(ns cayenne.elastic.mappings
  (:require [qbits.spandex :as elastic]))

(def contributor-properties
  {:contribution        {:type "keyword"}
   :given-name          {:type "text"}
   :family-name         {:type "text"}
   :org-name            {:type "text"}
   :prefix              {:type "text"}
   :suffix              {:type "text"}
   :orcid               {:type "keyword"}
   :affiliations        {:type "text"}
   :authenticated-orcid {:type "boolean"}})

(def issn-properties
  {:value {:type "keyword"}
   :kind  {:type "keyword"}})

(def isbn-properties
  {:value {:type "keyword"}
   :kind  {:type "keyword"}})

(def funder-properties
  {:funder-name {:type "text"}
   :funder-doi  {:type "keyword"}
   :awards      {:type "text"}})

(def update-properties)

(def clinical-trial-properties)

(def event-properties)

(def license-properties)

(def assertion-properties
  {:name            {:type "keyword"}
   :label           {:type "text"}
   :group-name      {:type "keyword"}
   :group-label     {:type "text"}
   :url             {:type "keyword"}
   :value           {:type "text"}
   :order           {:type "integer"}
   :explanation-url {:type "keyword"}})

(def relation-properties)

(def reference-properties)

(def work-properties
  {:random          {:type "long"}
   :kind            {:type "keyword"}
   :original-doi    {:type "keyword"}
   :normalised-doi  {:type "keyword"}
   :owner-prefix    {:type "keyword"}
   :member-id       {:type "integer"}
   :language-title  {:type "text"}
   :original-title  {:type "text"}
   :container-title {:type "text"}
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
   compatibility (which will remove multi-type per fields, making
   multiple types per index unworkable.)"
  [conn]
  (doseq [[index-name index-data] mapping-types]
    (elastic/request conn
                     :url index-name
                     :method :put
                     :body {:mappings {"doc" index-data}})))
