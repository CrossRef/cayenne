(ns cayenne.elastic.mappings
  (:require [qbits.spandex :as elastic]))

;; todo - particle dates

(def contributor-properties
  {:contribution        {:type "keyword"}
   :given-name          {:type "text"}
   :family-name         {:type "text"}
   :org-name            {:type "text"}
   :prefix              {:type "text"}
   :suffix              {:type "text"}
   :orcid               {:type "keyword"}
   :affiliation         {:type "text"}
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
   :award       {:type "text"}})

(def update-properties
  {:doi   {:type "keyword"}
   :type  {:type "keyword"}
   :label {:type "keyword"}
   :date  {:type "date"}})

(def clinical-trial-properties
  {:number   {:type "text"}
   :registry {:type "keyword"}
   :type     {:type "keyword"}})

(def link-properties
  {:type        {:type "keyword"}
   :url         {:type "keyword"}
   :version     {:type "keyword"}
   :application {:type "keyword"}})

(def event-properties
  {:name     {:type "text"}
   :theme    {:type "text"}
   :location {:type "text"}
   :sponsor  {:type "text"}
   :acronym  {:type "text"}
   :number   {:type "text"}
   :start    {:type "date"}
   :end      {:type "date"}})

(def license-properties
  {:version {:type "keyword"}
   :url     {:type "keyword"}
   :delay   {:type "integer"}
   :start   {:type "date"}})

(def assertion-properties
  {:name            {:type "keyword"}
   :label           {:type "text"}
   :group-name      {:type "keyword"}
   :group-label     {:type "text"}
   :url             {:type "keyword"}
   :value           {:type "text"}
   :order           {:type "integer"}
   :explanation-url {:type "keyword"}})

(def relation-properties
  {:type        {:type "keyword"}
   :object      {:type "keyword"}
   :object-type {:type "keyword"}
   :object-ns   {:type "keyword"}
   :claimed-by  {:type "keyword"}})

(def reference-properties
  {:key                  {:type "keyword"}
   :issn                 {:type "keyword"}
   :issn-type            {:type "keyword"}
   :author               {:type "text"}
   :issue                {:type "text"}
   :first-page           {:type "text"}
   :year                 {:type "integer"}
   :isbn                 {:type "keyword"}
   :isbn-type            {:type "keyword"}
   :series-title         {:type "text"}
   :volume-title         {:type "text"}
   :edition              {:type "keyword"}
   :component            {:type "keyword"}
   :volume               {:type "keyword"}
   :article-title        {:type "text"}
   :journal-title        {:type "text"}
   :standards-body       {:type "text"}
   :standards-designator {:type "keyword"}
   :doi-asserted-by      {:type "keyword"}
   :doi                  {:type "keyword"}})

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
   :issn            {:type "object" :properties issn-properties}
   :isbn            {:type "object" :properties isbn-properties}
   :contributor     {:type "nested" :properties contributor-properties}
   :funder          {:type "nested" :properties funder-properties}
   :updated-by      {:type "nested" :properties update-properties}
   :update-of       {:type "nested" :properties update-properties}
   :clinical-trial  {:type "nested" :properties clinical-trial-properties}
   :event           {:type "object" :properties event-properties}
   :link            {:type "nested" :properties link-properties}
   :license         {:type "nested" :properties license-properties}
   :assertion       {:type "nested" :properties assertion-properties}
   :relation        {:type "nested" :properties relation-properties}
   :reference       {:type "object" :properties reference-properties}})

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
  {"work"    {:_all: {:enabled false} :properties work-properties}})
   "member"  {:_all: {:enabled false} :properties member-properties}
   "funder"  {:_all: {:enabled false} :properties funder-properties}
   "prefix"  {:_all: {:enabled false} :properties prefix-properties}
   "subject" {:_all: {:enabled false} :properties subject-properties}
   "journal" {:_all: {:enabled false} :properties journal-properties}})
  
(defn create-indexes
  "Creates an index per top-level document type - in preparation for ES 6+
   compatibility (which will remove multi-type per fields, making
   multiple types per index unworkable.)"
  [conn]
  (doseq [[index-name index-data] mapping-types]
    (elastic/request conn
                     {:url index-name
                      :method :put
                      :body {:mappings {"doc" index-data}}})))
