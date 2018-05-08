(ns cayenne.elastic.mappings
  (:require [qbits.spandex :as elastic]))

(def contributor-properties
  {:contribution        {:type "keyword"}
   :given-name          {:type "text"}
   :family-name         {:type "text"}
   :org-name            {:type "text"}
   :prefix              {:type "text"}
   :suffix              {:type "text"}
   :sequence            {:type "text"}
   :orcid               {:type "keyword" :copy_to :contributor-orcid}
   :affiliation         {:type "keyword" :copy_to [:affiliation :affiliation-text]}
   :authenticated-orcid {:type "boolean"}})

(def issn-properties
  {:value {:type "keyword"}
   :type  {:type "keyword"}})

(def isbn-properties
  {:value {:type "keyword"}
   :type  {:type "keyword"}})

(def work-funder-properties
  {:name            {:type "keyword" :copy_to [:funder-name :funder-name-text]}
   :doi             {:type "keyword" :copy_to :funder-doi}
   :doi-asserted-by {:type "keyword"}
   :award           {:type "text"}})

(def update-properties
  {:doi   {:type "keyword"}
   :type  {:type "keyword" :copy_to :update-type}
   :label {:type "keyword"}
   :date  {:type "date"}})

(def clinical-trial-properties
  {:number   {:type "text"}
   :registry {:type "keyword"}
   :type     {:type "keyword"}})

(def link-properties
  {:content-type {:type "keyword"}
   :url          {:type "keyword"}
   :version      {:type "keyword"}
   :application  {:type "keyword" :copy_to :link-application}})

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
   :url     {:type "keyword" :copy_to :license-url}
   :delay   {:type "long"}
   :start   {:type "date"}})

(def assertion-properties
  {:name            {:type "keyword" :copy_to :assertion-name}
   :label           {:type "text"}
   :group-name      {:type "keyword" :copy_to :assertion-group-name}
   :group-label     {:type "text"}
   :url             {:type "keyword"}
   :value           {:type "text"}
   :order           {:type "integer"}
   :explanation-url {:type "keyword"}})

(def relation-properties
  {:type        {:type "keyword" :copy_to :relation-type}
   :object      {:type "keyword"}
   :object-type {:type "keyword"}
   :object-ns   {:type "keyword"}
   :claimed-by  {:type "keyword"}})

(def reference-properties
  {:key                  {:type "keyword"}
   :doi                  {:type "keyword"}
   :doi-asserted-by      {:type "keyword"}
   :issn                 {:type "keyword" :index false}
   :issn-type            {:type "keyword" :index false}
   :author               {:type "text" :index false}
   :issue                {:type "text" :index false}
   :first-page           {:type "text" :index false}
   :year                 {:type "text" :index false}
   :isbn                 {:type "keyword" :index false}
   :isbn-type            {:type "keyword" :index false}
   :series-title         {:type "text" :index false}
   :volume-title         {:type "text" :index false}
   :edition              {:type "keyword" :index false}
   :component            {:type "keyword" :index false}
   :volume               {:type "keyword" :index false}
   :article-title        {:type "text" :index false}
   :journal-title        {:type "text" :index false}
   :standards-body       {:type "text" :index false}
   :standards-designator {:type "keyword" :index false}
   :unstructured         {:type "text" :index false}})

(def standards-body-properties
  {:name    {:type "text"}
   :acronym {:type "text"}})

(def peer-review-properties
  {:running-number {:type "text" :index false}
   :revision-round {:type "text" :index false}
   :stage {:type "text" :index false}
   :recommendation {:type "text" :index false}
   :competing-interest-statement {:type "text" :index false}
   :type {:type "text" :index false}
   :language {:type "text" :index false}})

(def journal-issue-properties
  {:published-print {:type "date"}
   :published-online {:type "date"}
   :issue {:type "text"}})

(def work-properties
  {:metadata-content-text      {:type "text"}
   :source                     {:type "text"}
   :bibliographic-content-text {:type "text"}
   :title-text                 {:type "text"}
   :container-title-text       {:type "text"}
   :author-text                {:type "text"}
   :editor-text                {:type "text"}
   :chair-text                 {:type "text"}
   :translator-text            {:type "text"}
   :contributor-text           {:type "text"}
   :publisher-text             {:type "text"}
   :publisher-location-text    {:type "text"}
   :degree-text                {:type "text"}
   :affiliation-text           {:type "text"}
   :funder-name-text           {:type "text"}
   :abstract                   {:type "keyword" :index false}
   :abstract-xml               {:type "keyword" :index false}
   :type                       {:type "keyword"}
   :doi                        {:type "keyword"}
   :prefix                     {:type "keyword"}
   :owner-prefix               {:type "keyword"}
   :member-id                  {:type "integer"}
   :journal-id                 {:type "integer"}
   :book-id                    {:type "integer"}
   :citation-id                {:type "integer"}
   :supplementary-id           {:type "keyword"}
   :issued-year                {:type "integer"}
   :title                      {:type "keyword" :copy_to :title-text}
   :original-title             {:type "text"}
   :container-title            {:type "keyword" :copy_to :container-title-text}
   :short-container-title      {:type "text"}
   :short-title                {:type "text"}
   :group-title                {:type "text"}
   :subtitle                   {:type "keyword" :copy_to :title-text}
   :volume                     {:type "keyword"}
   :issue                      {:type "keyword"}
   :first-page                 {:type "keyword"}
   :last-page                  {:type "keyword"}
   :description                {:type "keyword"}
   :is-referenced-by-count     {:type "long"}
   :references-count           {:type "long"}
   :article-number             {:type "text"}
   :first-deposited            {:type "date"}
   :deposited                  {:type "date"}
   :indexed                    {:type "date"}
   :issued                     {:type "date"}
   :published                  {:type "date"}
   :published-online           {:type "date"}
   :published-print            {:type "date"}
   :published-other            {:type "date"}
   :posted                     {:type "date"}
   :accepted                   {:type "date"}
   :content-created            {:type "date"}
   :content-updated            {:type "date"}
   :approved                   {:type "date"}
   :subject                    {:type "keyword"}
   :publication                {:type "keyword"}
   :archive                    {:type "keyword"}
   :publisher                  {:type "keyword" :copy_to :publisher-text}
   :publisher-location         {:type "keyword" :copy_to :publisher-location-text}
   :degree                     {:type "keyword" :copy_to :degree-text}
   :edition-number             {:type "keyword"}
   :part-number                {:type "keyword"}
   :component-number           {:type "keyword"}
   :language                   {:type "text"}
   :update-policy              {:type "keyword"}
   :domain                     {:type "keyword"}
   :domain-exclusive           {:type "boolean"}
   :index-context              {:type "keyword"}
   :funder-name                {:type "keyword"}
   :funder-doi                 {:type "keyword"}
   :contributor-orcid          {:type "keyword"}
   :license-url                {:type "keyword"}
   :update-type                {:type "keyword"}
   :relation-type              {:type "keyword"}
   :contributor-affiliation    {:type "keyword"}
   :assertion-name             {:type "keyword"}
   :assertion-group-name       {:type "keyword"}
   :link-application           {:type "keyword"}
   :standards-body             {:type "object" :properties standards-body-properties}
   :issn                       {:type "object" :properties issn-properties}
   :isbn                       {:type "object" :properties isbn-properties}
   :contributor                {:type "nested" :properties contributor-properties}
   :funder                     {:type "nested" :properties work-funder-properties}
   :updated-by                 {:type "nested" :properties update-properties}
   :update-to                  {:type "nested" :properties update-properties}
   :clinical-trial             {:type "nested" :properties clinical-trial-properties}
   :event                      {:type "object" :properties event-properties}
   :link                       {:type "nested" :properties link-properties}
   :license                    {:type "nested" :properties license-properties}
   :assertion                  {:type "object" :properties assertion-properties}
   :relation                   {:type "nested" :properties relation-properties}
   :reference                  {:type "object" :properties reference-properties}
   :peer-review                {:type "object" :properties peer-review-properties}
   :institution                {:type "object"}
   :free-to-read               {:type "object"}
   :journal-issue              {:type "object" :properties journal-issue-properties}})

(def prefix-properties
  {:value                {:type "keyword"}
   :member-id            {:type "integer"}
   :reference-visibility {:type "keyword"}
   :location             {:type "text"}
   :name                 {:type "text"}})

(def member-properties
  {:primary-name {:type "text" :copy_to :suggest}
   :suggest      {:type "completion"}
   :location     {:type "text"}
   :id           {:type "long"}
   :token        {:type "keyword"}
   :prefix       {:type "object" :properties prefix-properties}})

(def funder-properties
  {:doi             {:type "keyword"}
   :suggest         {:type "completion"}
   :id              {:type "text"}
   :level           {:type "integer"}
   :parent          {:type "keyword"}
   :ancestor        {:type "keyword"}
   :child           {:type "keyword"}
   :descendant      {:type "keyword"}
   :hierarchy       {:type "object" :enabled false}
   :hierarchy-names {:type "object" :enabled false}
   :affiliated      {:type "keyword"}
   :country         {:type "keyword"}
   :primary-name    {:type "text" :copy_to :suggest}
   :name            {:type "text" :copy_to :suggest}
   :replaces        {:type "keyword"}
   :replaced-by     {:type "keyword"}
   :token           {:type "keyword"}})

(def subject-properties
  {:high-code   {:type "integer"}
   :code        {:type "integer"}
   :name        {:type "keyword"}})

(def journal-properties
  {:title     {:type "text" :copy_to :suggest}
   :suggest   {:type "completion"}
   :token     {:type "keyword"}
   :id        {:type "long"}
   :doi       {:type "keyword"}
   :publisher {:type "text"}
   :subject   {:type "object" :properties subject-properties}
   :issn      {:type "object" :properties issn-properties}})

(def coverage-properties
  {:subject-type  {:type "keyword"}
   :subject-id    {:type "long"}
   :started       {:type "date"}
   :finished      {:type "date"}
   :total-dois    {:type "long"}
   :backfile-dois {:type "long"}
   :current-dois  {:type "long"}
   :breakdowns    {:type "object"}
   :coverage      {:type "object"}})

(def index-mappings
  {"work"     {"_all" {:enabled false} :properties work-properties}
   "member"   {"_all" {:enabled false} :properties member-properties}
   "funder"   {"_all" {:enabled false} :properties funder-properties}
   "subject"  {"_all" {:enabled false} :properties subject-properties}
   "coverage" {"_all" {:enabled false} :properties coverage-properties}
   "journal"  {"_all" {:enabled false} :properties journal-properties}})

(def index-settings
  {"work"     {:number_of_shards 1  :number_of_replicas 3}
   "member"   {:number_of_shards 1  :number_of_replicas 3}
   "funder"   {:number_of_shards 1  :number_of_replicas 3}
   "subject"  {:number_of_shards 1  :number_of_replicas 3}
   "coverage" {:number_of_shards 1  :number_of_replicas 3}
   "journal"  {:number_of_shards 1  :number_of_replicas 3}})

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
