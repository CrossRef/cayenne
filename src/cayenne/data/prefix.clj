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

(def solr-prefix-id-field "owner_prefix")

(defn get-solr-work-count [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context 
                                  :id-field solr-publisher-id-field
                                  :paged false
                                  :count-only true))
      (.getResults)
      (.getNumFound)))

(defn fetch-works [query-context]
  (work/fetch query-context :id-field solr-prefix-id-field))

(defn fetch-one [query-context]
  (when-let [member-doc (m/with-mongo (conf/get-service :mongo)
                          (m/fetch-one
                           "members"
                           :where {:prefixes (prefix-id/extract-prefix (:id query-context))}))]
    (r/api-response :prefix :content {:member (member-id/to-member-id-uri (:id member-doc))
                                      :name (:primary-name member-doc)
                                      :prefix (prefix-id/to-prefix-uri (:id query-context))})))
