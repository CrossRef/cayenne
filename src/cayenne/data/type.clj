(ns cayenne.data.type
  (:require [cayenne.api.v1.response :as r]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.filter :as filter]
            [cayenne.formats.citeproc :as citeproc]
            [cayenne.conf :as conf]))

(def type-dictionary {:journal-article {:index-id "Journal Article" 
                                        :label "Journal Article"}
                      :journal-issue {:index-id "Journal Issue"
                                      :label "Journal Issue"}
                      :journal-volume {:index-id "Journal Volume" 
                                       :label "Journal Volume"}
                      :journal {:index-id "Journal"
                                :label "Journal"}
                      :proceedings-article {:index-id "Proceedings Article" 
                                            :label "Proceedings Article"}
                      :dataset {:index-id "Dataset"
                                :label "Dataset"}
                      :report {:index-id "Report"
                               :label "Report"}
                      :report-series {:index-id "Report Series"
                                      :label "Report Series"}
                      :standard {:index-id "Standard"
                                 :label "Standard"}
                      :standard-series {:index-id "Standard Series"
                                        :label "Standard Series"}
                      :edited-book {:index-id "Edited Book"
                                    :label "Edited Book"}
                      :monograph {:index-id "Monograph"
                                  :label "Monograph"}
                      :reference-book {:index-id "Reference Book"
                                       :label "Reference Book"}
                      :book {:index-id "Book"
                             :label "Book"}
                      :book-series {:index-id "Book Series"
                                    :label "Book Series"}
                      :book-set {:index-id "Book Set"
                                 :label "Book Set"}
                      :book-chapter {:index-id "Chapter"
                                     :label "Book Chapter"}
                      :book-section {:index-id "Section"
                                     :label "Book Section"}
                      :book-part {:index-id "Part"
                                  :label "Book Part"}
                      :book-track {:index-id "Track"
                                   :label "Book Track"}
                      :book-entry {:index-id "Reference Entry"
                                   :label "Book Entry"}
                      :other {:index-id "Other"
                              :label "Other"}})

(defn ->index-id [id]
  (when-let [t (get type-dictionary (keyword id))]
    (:index-id t)))

(defn ->pretty-type [id t]
  {:id id
   :label (:label t)})

(def solr-type-id-field "type")

(defn get-solr-works [query-context]
  (let [ctxt (update-in query-context [:id] ->index-id)]
    (-> (conf/get-service :solr)
        (.query (query/->solr-query ctxt
                                    :id-field solr-type-id-field
                                    :filters filter/std-filters))
        (.getResults))))

(defn fetch-all []
  (-> (r/api-response :type-list)
      (r/with-result-items 
        (count type-dictionary)
        (map (fn [[id t]] (->pretty-type id t)) type-dictionary))))

(defn fetch-one [query-context]
  (let [query-id (:id query-context)
        type-info (->> query-id
                       keyword
                       (get type-dictionary))]
    (when type-info
      (->> type-info
           (->pretty-type query-id)
           (r/api-response :type :content)))))

(defn fetch-works [query-context]
  (let [doc-list (get-solr-works query-context)]
    (-> (r/api-response :work-list)
        (r/with-query-context-info query-context)
        (r/with-result-items 
          (.getNumFound doc-list)
          (map citeproc/->citeproc doc-list)))))
