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
   :affiliation         {:type "text"}
   :authenticated-orcid {:type "boolean"}})

(def issn-properties
  {:value {:type "keyword"}
   :kind  {:type "keyword"}})

(def isbn-properties
  {:value {:type "keyword"}
   :kind  {:type "keyword"}})

(def funder-properties
  {:name            {:type "text"}
   :doi             {:type "keyword"}
   :doi-asserted-by {:type "keyword"}
   :award           {:type "text"}})

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

(def standards-body-properties
  {:name    {:type "text"}
   :acronym {:type "text"}})

;; todo content, citation_content
(def work-properties
  {:random                {:type "long"}
   :type                  {:type "keyword"}
   :doi                   {:type "keyword"}
   :prefix                {:type "keyword"}
   :owner-prefix          {:type "keyword"}
   :member-id             {:type "integer"}
   :supplementary-id      {:type "keyword"}
   :language-title        {:type "text"}
   :original-title        {:type "text"}
   :container-title       {:type "text"}
   :short-container-title {:type "text"}
   :short-title           {:type "text"}
   :group-title           {:type "text"}
   :subtitle              {:type "text"}
   :volume                {:type "keyword"}
   :issue                 {:type "keyword"}
   :first-page            {:type "keyword"}
   :last-page             {:type "keyword"}
   :description           {:type "text"}
   :referenced-by-count   {:type "long"}
   :references-count      {:type "long"}
   :article-number        {:type "text"}
   :first-deposited       {:type "date"}
   :deposited             {:type "date"}
   :indexed               {:type "date"}
   :published             {:type "date"}
   :published-online      {:type "date"}
   :published-print       {:type "date"}
   :published-other       {:type "date"}
   :posted                {:type "date"}
   :accepted              {:type "date"}
   :content-created       {:type "date"}
   :content-updated       {:type "date"}
   :approved              {:type "date"}
   :subject               {:type "keyword"}
   :publication           {:type "text"}
   :archive               {:type "keyword"}
   :publisher             {:type "text"}
   :publisher-location    {:type "text"}
   :degree                {:type "text"}
   :edition-number        {:type "keyword"}
   :part-number           {:type "keyword"}
   :component-number      {:type "keyword"}
   :update-policy         {:type "keyword"}
   :domain                {:type "keyword"}
   :domain-exclusive      {:type "boolean"}
   :abstract              {:type "text"}
   :abstract-xml          {:type "text"}
   :index-context         {:type "keyword"}
   :standards-body        {:type "object" :properties standards-body-properties}
   :issn                  {:type "object" :properties issn-properties}
   :isbn                  {:type "object" :properties isbn-properties}
   :contributor           {:type "nested" :properties contributor-properties}
   :funder                {:type "nested" :properties funder-properties}
   :updated-by            {:type "nested" :properties update-properties}
   :update-of             {:type "nested" :properties update-properties}
   :clinical-trial        {:type "nested" :properties clinical-trial-properties}
   :event                 {:type "object" :properties event-properties}
   :link                  {:type "nested" :properties link-properties}
   :license               {:type "nested" :properties license-properties}
   :assertion             {:type "nested" :properties assertion-properties}
   :relation              {:type "nested" :properties relation-properties}
   :reference             {:type "object" :properties reference-properties}})

(def prefix-properties
  {:value             {:type "keyword"}
   :member-id         {:type "integer"}
   :public-references {:type "boolean"}
   :location          {:type "text"}
   :name              {:type "text"}})

;; todo metadata coverage fields
(def member-properties
  {:primary-name {:type "text"}
   :location     {:type "text"}
   :id           {:type "long"}
   :token        {:type "keyword"}
   :prefix       {:type "object" :properties prefix-properties}})

;; todo metadata coverage fields
(def funder-properties
  {:doi          {:type "keyword"}
   :parent       {:type "keyword"}
   :child        {:type "keyword"}
   :affiliated   {:type "keyword"}
   :country      {:type "keyword"}
   :primary-name {:type "text"}
   :name         {:type "text"}
   :replaces     {:type "keyword"}
   :replaced-by  {:type "keyword"}
   :token        {:type "keyword"}})

(def subject-properties
  {:high-code   {:type "integer"}
   :code        {:type "integer"}
   :name        {:type "keyword"}})

;; todo metadata coverage fields
(def journal-properties
  {:title     {:type "text"}
   :token     {:type "keyword"}
   :id        {:type "long"}
   :doi       {:type "keyword"}
   :publisher {:type "text"}
   :subject   {:type "object" :properties subject-properties}
   :issn      {:type "object" :properties issn-properties}})
   
(def index-mappings
  {"work"    {"_all" {:enabled false} :properties work-properties}
   "member"  {"_all" {:enabled false} :properties member-properties}
   "funder"  {"_all" {:enabled false} :properties funder-properties}
   "subject" {"_all" {:enabled false} :properties subject-properties}
   "journal" {"_all" {:enabled false} :properties journal-properties}})

(def index-settings
  {"work"    {:number_of_shards 24 :number_of_replicas 3 "index.mapper.dynamic" false}
   "member"  {:number_of_shards 1  :number_of_replicas 3 "index.mapper.dynamic" false}
   "funder"  {:number_of_shards 1  :number_of_replicas 3 "index.mapper.dynamic" false}
   "subject" {:number_of_shards 1  :number_of_replicas 3 "index.mapper.dynamic" false}
   "journal" {:number_of_shards 1  :number_of_replicas 3 "index.mapper.dynamic" false}})
  
(defn create-indexes
  "Creates an index per top-level document type - in preparation for ES 6+
   compatibility (which will remove multi-type per fields, making
   multiple types per index unworkable.)"
  [conn]
  (doseq [[index-name index-data] index-mappings]
    (elastic/request conn
                     {:url index-name
                      :method :put
                      :body {:settings (index-settings index-name)
                             :mappings {index-name index-data}}})))
