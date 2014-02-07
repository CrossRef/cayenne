(ns cayenne.data.prefix
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.filter :as filter]
            [cayenne.formats.citeproc :as citeproc]
            [cayenne.ids.prefix :as prefix]
            [clojure.string :as string]
            [somnium.congomongo :as m]))

(def solr-publisher-id-field "owner_prefix")

(defn get-solr-works [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context 
                                  :id-field solr-publisher-id-field
                                  :filters filter/std-filters))
      (.getResults)))

(defn get-solr-work-count [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context 
                                  :id-field solr-publisher-id-field
                                  :paged false
                                  :count-only true))
      (.getResults)
      (.getNumFound)))

(defn fetch-works [query-context]
  (let [doc-list (get-solr-works query-context)]
    (-> (r/api-response :work-list)
        (r/with-query-context-info query-context)
        (r/with-result-items 
          (.getNumFound doc-list)
          (map citeproc/->citeproc doc-list)))))

(defn fetch-one [query-context]
  (let [any-work (-> (assoc query-context :rows (int 1))
                     (get-solr-works)
                     (first))
        citeproc-doc (if-not (nil? any-work) (citeproc/->citeproc any-work) {})]
    (if (and 
         (not (nil? any-work))
         (not (string/blank? (:publisher citeproc-doc))))
      (r/api-response :prefix :content {:prefix (:id query-context)
                                        :name (:publisher citeproc-doc)})
      (r/api-response :prefix :content {:prefix (:id query-context)}))))

