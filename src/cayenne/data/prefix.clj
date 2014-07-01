(ns cayenne.data.prefix
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.filter :as filter]
            [cayenne.api.v1.facet :as facet]
            [cayenne.data.work :as work]
            [cayenne.formats.citeproc :as citeproc]
            [cayenne.ids.member :as member-id]
            [cayenne.ids.prefix :as prefix-id]
            [clojure.string :as string]
            [somnium.congomongo :as m]))

(def solr-publisher-id-field "owner_prefix")

(defn get-solr-works [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context 
                                  :id-field solr-publisher-id-field
                                  :filters filter/std-filters))))

(defn get-solr-work-count [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context 
                                  :id-field solr-publisher-id-field
                                  :paged false
                                  :count-only true))
      (.getResults)
      (.getNumFound)))

(defn fetch-works [query-context]
  (let [response (get-solr-works query-context)
        doc-list (.getResults response)]
    (-> (r/api-response :work-list)
        (r/with-result-facets (facet/->response-facets response))
        (r/with-query-context-info query-context)
        (r/with-result-items 
          (.getNumFound doc-list)
          (map (comp work/with-member-id citeproc/->citeproc) doc-list)))))

(defn fetch-one [query-context]
  (let [member-doc (m/with-mongo (conf/get-service :mongo)
                     (m/fetch-one
                      "members"
                      :where {:prefixes (prefix-id/extract-prefix (:id query-context))}))]
    (r/api-response :prefix :content {:member (member-id/to-member-id-uri (:id member-doc))
                                      :name (:primary-name member-doc)
                                      :prefix (prefix-id/to-prefix-uri (:id query-context))})))
