(ns cayenne.data.funder
  (:require [cayenne.conf :as conf]
            [cayenne.api.query :as query]
            [cayenne.api.response :as r]
            [cayenne.formats.citeproc :as citeproc]
            [cayenne.ids.fundref :as fr-id]
            [somnium.congomongo :as m]))

(def solr-funder-id-field "funder_doi")

(defn get-solr-works [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context :id-field solr-funder-id-field))
      (.getResults)))

(defn get-solr-work-count [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context 
                                  :id-field solr-funder-id-field
                                  :paged false
                                  :count-only true))
      (.getResults)
      (.getNumFound)))

(defn fetch-one [query-context]
  (let [funder-doc (m/with-mongo (conf/get-service :mongo)
                     (m/fetch-one "funders" 
                                  :where {:id (-> query-context
                                                  (:id)
                                                  (fr-id/doi-uri-to-id))}))]
    (when funder-doc
      (r/api-response :funder-summary
                      :content
                      {:id (:id query-context)
                       :location (:country funder-doc)
                       :name (:primary_name_display funder-doc)
                       :alt-names (:other_names_display funder-doc)
                       :uri (:uri funder-doc)
                       :tokens (:name_tokens funder-doc)
                       :work-count (get-solr-work-count query-context)}))))

(defn fetch [query-context]
  ())

(defn fetch-works [query-context]
  (let [doc-list (-> (conf/get-service :solr)
                     (.query (query/->solr-query query-context :id-field solr-funder-id-field))
                     (.getResults))]
    (-> (r/api-response :funder-work-result-list)
        (r/with-query-context-info query-context)
        (r/with-result-items (.getNumFound doc-list) (map citeproc/->citeproc doc-list)))))
       
      

