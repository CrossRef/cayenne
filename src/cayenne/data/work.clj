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
            [cayenne.formats.citeproc :as citeproc]
            [somnium.congomongo :as m]
            [org.httpkit.client :as http]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [cayenne.ids.doi :as doi]
            [clojure.set :as set])
  (:import [java.lang RuntimeException]
           [java.net URLEncoder]))

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

(def reference-visibilities
  {"open" ["open"]
   "limited" ["open" "limited"]
   "closed" ["open" "limited" "closed"]})

(defn display-citations? [metadata]
  (when (:member metadata)
    (let [prefix (prefix-id/extract-prefix (:prefix metadata))
          member-id (member-id/extract-member-id (:member metadata))
          member-prefix-info (get-member-prefix-info "members" member-id)
          visibilities (or (-> [:service :api :references]
                               conf/get-param
                               reference-visibilities)
                           ["open"])]
      (not
       (empty?
        (filter #(and (= (:value %) prefix)
                      (some #{(:reference-visibility %)} visibilities))
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
  (into
   {}
   (filter
    #(not (if (coll? (second %)) (empty? (second %)) (nil? (second %))))
    (if (empty? (:select query-context))
      (-> doc
          citeproc/->citeproc
          with-member-id
          with-citations)
      (-> doc
          citeproc/->citeproc
          (select-keys (map keyword (:select query-context)))
          with-member-id
          with-citations)))))

(defn indexed
  [s]
  (map vector (iterate inc 0) s))

(defn positions [pred coll]
  (for [[idx elt] (indexed coll) :when (pred elt)] idx))

(defn reordered-preprints [records]
  (let [reordering-records (take 20 records)
        doi-positions (into {} (map-indexed
                                #(vector (:DOI %2) %1)
                                reordering-records))]
    (concat
     (reduce
      #(let [has-preprint-dois (->> (get (:relation %2) "has-preprint")
                                    (map :id))
             preprint-positions (positions (fn [a]
                                             (some #{(:DOI a)}
                                                   has-preprint-dois)) %1)
             first-preprint-pos (if (empty? preprint-positions)
                                  -1
                                  (apply min preprint-positions))]
         (cond
           (empty? has-preprint-dois)
           (conj %1 %2)

           (not= first-preprint-pos -1)
           (vec
            (concat
             (subvec %1 0 first-preprint-pos)
             [%2]
             (subvec %1 first-preprint-pos)))

           :else
           (conj %1 %2)))
      []
      reordering-records)
     (drop 20 records))))

(defn fetch [query-context & {:keys [id-field] :or {id-field nil}}]
  (let [response (-> (conf/get-service :solr)
                     (.query (query/->solr-query query-context
                                                 :id-field id-field
                                                 :filters filter/std-filters)))
        doc-list (.getResults response)]
    (if (partial-response? response)
      (throw (RuntimeException. "Solr returned a partial result set"))
      (-> (r/api-response :work-list)
          (r/with-debug-info response query-context)
          (r/with-result-facets (facet/->response-facets response))
          (r/with-result-items
            (.getNumFound doc-list)
            (-> (map render-record (repeat query-context) doc-list)
                reordered-preprints)
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

(def agency-label
  {"10.SERV/DEFAULT"  "Default"
   "10.SERV/CROSSREF" "Crossref"
   "10.SERV/DATACITE" "DataCite"
   "10.SERV/MEDRA"    "mEDRA"})

(def agency-id
  {"10.SERV/DEFAULT"  "default"
   "10.SERV/CROSSREF" "crossref"
   "10.SERV/DATACITE" "datacite"
   "10.SERV/MEDRA"    "medra"})

(defn get-agency [doi]
  (let [extracted-doi (doi-id/normalize-long-doi doi)
        {:keys [status headers body error]}
        @(http/get (str (conf/get-param [:upstream :ra-url])
                        (doi-id/extract-long-prefix extracted-doi)))]
    (when-not error
      (let [vals (-> body
                     (json/read-str :key-fn keyword)
                     :values)
            hs-serv-index-value 1]
        (get-in
         (first
          (filter
           #(= (:index %) hs-serv-index-value)
           vals))
         [:data :value])))))

(defn ->agency-response [doi agency]
  (r/api-response 
   :work-agency
   :content {:DOI (doi-id/normalize-long-doi doi)
             :agency {:id (or (agency-id agency) agency)
                      :label (or (agency-label agency) agency)}}))

(defn fetch-agency [doi]
  (let [extracted-doi (doi-id/normalize-long-doi doi)
        agency (get-agency extracted-doi)]
    (->agency-response doi agency)))
