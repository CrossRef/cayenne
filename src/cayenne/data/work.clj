(ns cayenne.data.work
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.filter :as filter]
            [cayenne.api.v1.facet :as facet]
            [cayenne.data.quality :as quality]
            [cayenne.ids.doi :as doi-id]
            [cayenne.ids.prefix :as prefix-id]
            [cayenne.ids.member :as member-id]
            [cayenne.action :as action]
            [cayenne.formats.citeproc :as citeproc]
            [somnium.congomongo :as m]
            [org.httpkit.client :as http]
            [clojure.string :as string]
            [clojure.data.json :as json])
  (:import [java.lang RuntimeException]))

;; todo eventually produce citeproc from more detailed data stored in mongo
;; for each DOI that comes back from solr. For now, covert the solr fields
;; to some (occasionally ambiguous) citeproc structures.

;; todo API links - orcids, subject ids, doi, issn, isbn, owner prefix

;; todo conneg. currently returning two different formats - item-tree
;; where a DOI is known, citeproc for search results.

;; todo should be included in solr data
(defn get-id-for-prefix [collection prefix]
  (m/with-mongo (conf/get-service :mongo)
    (-> collection 
        (m/fetch-one :where {:prefixes (prefix-id/extract-prefix prefix)})
        :id
        member-id/to-member-id-uri)))

(defn get-member-prefix-info [collection id]
  (m/with-mongo (conf/get-service :mongo)
    (-> collection
        (m/fetch-one :where {:id (Integer/parseInt (member-id/extract-member-id id))})
        :prefix)))

(defn with-member-id [metadata]
  (if (:member metadata)
    metadata
    (assoc metadata :member (get-id-for-prefix "members" (:prefix metadata)))))

(defn display-citations? [metadata]
  (when (:member metadata)
    (let [prefix (prefix-id/extract-prefix (:prefix metadata))
          member-id (member-id/extract-member-id (:member metadata))
          member-prefix-info (get-member-prefix-info "members" member-id)]
      (not
       (empty?
        (filter #(and (= (:value %) prefix)
                      (:public-references %))
                member-prefix-info))))))

(defn with-citations [metadata]
  (if (display-citations? metadata)
    metadata
    (-> metadata
        (dissoc :reference)
        (update-in [:relation] dissoc :cites))))

(defn partial-response? [query-response]
  (.. query-response (getResponseHeader) (get "partialResults")))

(defn render-record [query-context doc]
  (cond
    (some #{"DOI"} (:select query-context))
    {:DOI (doi-id/extract-long-doi (get doc "doi"))}
    
    :else
    (-> doc
        citeproc/->citeproc
        with-member-id
        with-citations)))

(defn fetch [query-context & {:keys [id-field] :or {id-field nil}}]
  (let [response (-> (conf/get-service :solr)
                     (.query (query/->solr-query query-context
                                                 :id-field id-field
                                                 :filters filter/std-filters)))
        doc-list (.getResults response)]
    (if (partial-response? response)
      (throw (RuntimeException. "Solr returned a partial result set"))
      (-> (r/api-response :work-list)
          ;; (r/with-solr-debug-info response)
          (r/with-result-facets (facet/->response-facets response))
          (r/with-result-items
            (.getNumFound doc-list)
            (map render-record (repeat query-context) doc-list)
            :next-cursor (.getNextCursorMark response))
          (r/with-query-context-info query-context)))))

(defn fetch-reverse [query-context]
  (let [terms (query/clean-terms (:terms query-context) :remove-syntax true)
        q (str "content_citation:(" terms ")")
        response (-> (conf/get-service :solr)
                     (.query (query/->solr-query {:raw-terms q
                                                  :rows (int 1)})))
        doc-list (.getResults response)]
    (if (partial-response? response)
      (throw (RuntimeException. "Solr returned a partial result set"))
      (if (zero? (.getNumFound doc-list))
        (r/api-response :nothing)
        (let [doc (-> doc-list first citeproc/->citeproc with-member-id)]
          (if (or (< (count (string/split terms #"\s")) 4) (< (:score doc) 2))
            (r/api-response :nothing)
            (r/api-response :work :content doc)))))))

(defn fetch-one
  "Fetch a known DOI."
  [doi-uri]
  (let [response (-> (conf/get-service :solr)
                     (.query (query/->solr-query {:id doi-uri}
                                                 :id-field "doi")))]
    (if (partial-response? response)
      (throw (RuntimeException. "Solr returned a partail result set"))
      (when-let [doc (-> response (.getResults) first)]
        (r/api-response :work :content (-> (citeproc/->citeproc doc)
                                           with-member-id
                                           with-citations))))))

(defn get-unixsd [doi]
  (let [record (promise)]
    (action/parse-doi doi (action/return-item record))
    (second @record)))

(defn fetch-quality
  [doi]
  (let [item-tree (get-unixsd doi)]
    (r/api-response :work-quality :content (quality/check-tree item-tree))))

(def agency-label
  {:crossref "CrossRef"
   :datacite "DataCite"
   :medra "mEDRA"})

(defn get-agency [doi]
  (let [extracted-doi (doi-id/normalize-long-doi doi)
        {:keys [status headers body error]}
        @(http/get (str (conf/get-param [:upstream :ra-url]) extracted-doi))]
    (when-not error
      (let [agency (-> body json/read-str first (get "RA"))]
        (when-not (nil? agency)
          (-> agency
              string/lower-case
              (string/replace #"\s+" "")
              keyword))))))

(defn ->agency-response [doi agency]
  (r/api-response 
   :work-agency
   :content {:DOI (doi-id/normalize-long-doi doi)
             :agency {:id agency :label (or (agency-label agency) agency)}}))

(defn fetch-agency [doi]
  (let [extracted-doi (doi-id/normalize-long-doi doi)
        agency (get-agency extracted-doi)]
    (->agency-response doi agency)))
