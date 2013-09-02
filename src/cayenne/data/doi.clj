(ns cayenne.data.doi
  (:require [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi-id]
            [cayenne.api.query :as query]
            [cayenne.api.response :as r]))

;; todo eventually produce citeproc from more detailed data stored in mongo
;; for each DOI that comes back from solr. For now, covert the solr fields
;; to some (occasionally ambiguous) citeproc structures.

(defn solr-doc->citeproc [solr-doc]
  {:volume (get solr-doc "hl_volume")
   :issue (get solr-doc "hl_issue")
   :DOI (doi-id/extract-long-doi (get solr-doc "doi"))
   :URL (get solr-doc "doi")
   :title (set (get solr-doc "hl_title"))
   :container-title (set (get solr-doc "hl_publication"))
   :issued ""
   :deposited ""
   :author ""
   :editor ""
   :chair ""
   :contributor ""
   :page (str (get solr-doc "hl_first_page") "-" (get solr-doc "hl_last_page"))
   :type (get solr-doc "type")
   :subject (get solr-doc "category")})


(defn fetch-dois [query-context]
  (let [doc-list (-> (conf/get-service :solr)
                     (.query (query/->solr-query query-context))
                     (.getResults))]
    (-> (r/api-response :doi-result-list)
        (r/with-result-items (.getNumFound doc-list) (map solr-doc->citeproc doc-list))
        (r/with-query-context-info query-context))))

        


