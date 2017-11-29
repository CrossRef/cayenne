(ns cayenne.data.work
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.filter :as filter]
            [cayenne.api.v1.facet :as facet]
            [cayenne.ids.doi :as doi-id]
            [cayenne.ids.prefix :as prefix-id]
            [cayenne.ids.member :as member-id]
            [org.httpkit.client :as http]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [cayenne.ids.doi :as doi]
            [clojure.set :as set]
            [qbits.spandex :as elastic]
            [cayenne.elastic.convert :as convert])
  (:import [java.lang RuntimeException]
           [java.net URLEncoder]))

(defn display-citations? [metadata]
  (when (:member metadata)
    (when-let [member-doc (-> (elastic/request
                               (conf/get-service :elastic)
                               {:method :get
                                :url "/member/member/_search"
                                :body (-> {:size 1}
                                          (assoc-in
                                           [:query :bool :filter]
                                           [{:term {:id (:member metadata)}}]))})
                              (get-in [:body :hits :hits])
                              first
                              :_source)]
      (not
       (empty?
        (filter #(and (= (:value %) (:prefix metadata))
                      (= (:reference-visibility %) "open"))
                (:prefix member-doc)))))))

(defn with-citations [metadata]
  (if (display-citations? metadata)
    metadata
    (-> metadata
        (dissoc :reference))))

(defn render-record [query-context doc]
  (into
   {}
   (filter
    #(not (if (coll? (second %)) (empty? (second %)) (nil? (second %))))
    (if (empty? (:select query-context))
      (-> doc
          convert/es-doc->citeproc
          with-citations)
      (-> doc
          convert/es-doc->citeproc
          (select-keys (map keyword (:select query-context)))
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
  (let [es-request (query/->es-request query-context
                                       :id-field id-field
                                       :filters filter/std-filters)
        response (elastic/request (conf/get-service :elastic) es-request)
        doc-list (get-in response [:body :hits :hits])]
    (-> (r/api-response :work-list)
        (r/with-debug-info query-context es-request)
        (r/with-result-facets (-> response
                                  (get-in [:body :aggregations])
                                  facet/->response-facets))
        (r/with-result-items
          (get-in response [:body :hits :total])
          (-> (map render-record (repeat query-context) doc-list)
              reordered-preprints)
          :next-cursor (get-in response [:body :_scroll_id]))
        (r/with-query-context-info query-context))))

(defn fetch-reverse [query-context]
  (let [terms (:terms query-context)
        es-request (query/->es-request {:field-terms {"bibliographic" terms}
                                        :rows 1})
        response (elastic/request (conf/get-service :elastic) es-request)
        doc-list (get-in response [:body :hits :hits])]
    (if (zero? (count doc-list))
      (r/api-response :nothing)
      (let [doc (-> doc-list first convert/es-doc->citeproc)]
        (if (or (< (count (string/split terms #"\s")) 4) (< (:_score doc) 2))
          (r/api-response :nothing)
          (r/api-response :work :content doc))))))

(defn fetch-one
  "Fetch a known DOI."
  [doi]
  (let [response (elastic/request
                  (conf/get-service :elastic)
                  (query/->es-request {:id doi}
                                      :id-field :doi
                                      :paged false))]
    (when-let [doc (first (get-in response [:body :hits :hits]))]
      (r/api-response :work :content (-> doc
                                         convert/es-doc->citeproc
                                         with-citations)))))

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
