(ns cayenne.tasks.update
  (:require [cayenne.conf :as conf]
            [cayenne.data.work :as work]
            [cayenne.tasks.solr :as solr]
            [cayenne.ids.doi :as doi-id]
            [cayenne.formats.citeproc :as citeproc])
  (:import [org.apache.solr.client.solrj SolrQuery]))

(defn update-solr-doc-with-update-by [solr-doc update from-doi]
  (-> solr-doc
      (.addField "update_by_doi" (doi-id/to-long-doi-uri from-doi))
      (.addField "update_by_type" (:type update))
      (.addField "update_by_label" (:label update))
      (.addField "update_by_date" (-> update :updated first solr/as-datetime))))

(defn write-update! [update from-doi]
  (-> (conf/get-service :solr)
      (.query (SolrQuery. (str "doi_key:\"" (doi-id/to-long-doi-uri from-doi) "\"")))
      (.getResults)
      first
      (update-solr-doc-with-update-by update from-doi)
      solr/insert-solr-doc))

(defn write-updates! [metadata]
  (doseq [update (:update-to metadata)]
    (write-update! update (:DOI metadata))))

(defn write-all-updates! [& {:keys [offset] :or {offset 0}}]
  (let [rows 1000
        update-docs-response (work/fetch {:filter {:is-update "true"} 
                                          :rows rows
                                          :offset offset})
        update-docs (-> update-docs-response :message :items)]
    (doseq [update-doc update-docs]
      (write-updates! update-doc))
    (when-not (zero? (count update-docs))
      (recur [:offset (+ offset rows)]))))
    
    
