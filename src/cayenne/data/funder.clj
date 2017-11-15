(ns cayenne.data.funder
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.filter :as filter]
            [cayenne.api.v1.facet :as facet]
            [cayenne.data.work :as work]
            [cayenne.ids.fundref :as fr-id]
            [clojure.string :as string]
            [cayenne.ids.doi :as doi-id]
            [qbits.spandex :as elastic]))

(defn get-solr-works [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context 
                                  :id-field solr-funder-id-field
                                  :filters filter/std-filters))))

(defn get-solr-work-count 
  "Get work count from solr for a mongo funder doc."
  [funder-doc]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query {:id (:uri funder-doc)}
                                  :id-field solr-funder-id-field
                                  :paged false
                                  :count-only true))
      (.getResults)
      (.getNumFound)))

(defn get-solr-descendant-work-count
  [funder-doc]
  (let [ids (vec (conj (map fr-id/id-to-doi-uri (:descendants funder-doc))
                  (:uri funder-doc)))]
    (-> (conf/get-service :solr)
        (.query (query/->solr-query {:id ids}
                                    :id-field solr-funder-id-field
                                    :paged false
                                    :count-only true))
        (.getResults)
        (.getNumFound))))

(defn fetch-descendant-dois
  "Get all descendant funder ids for a funder."
  [funder-doi]
  (-> (elastic/request
       (conf/get-service :elastic)
       {:method :get
        :url (str "/funder/funder/" funder-doi)})
      (get-in [:body :hits :hits])
      first
      (get-in [:_source :descendant])))

(defn fetch-descendant-work-count [funder-doi]
  (let [funder-dois (conj (fetch-descendant-dois funder-doi)
                          funder-doi)
        nested-query {:bool
                      {:should
                       (map #(hash-map :term {:funder.doi %}) funder-dois)}}]
    (-> (elastic/request
         (conf/get-service :elastic)
         {:method :get
          :url "/work/work/_count"
          :body {:query {:nested {:path :funder :query nested-query}}}})
        (get-in [:body :count]))))

(defn fetch-work-count [funder-doi]
  (-> (elastic/request
       (conf/get-service :elastic)
       {:method :get
        :url "/work/work/_count"
        :body (assoc-in
               {}
               [:query :nested]
               {:path :funder
                :query {:term {:funder.doi funder-doi}}})})
      (get-in [:body :count])))

(defn ->response-doc [funder-doc]
  {:id          (:doi funder-doc)
   :location    (:country funder-doc)
   :name        (:primary-name funder-doc)
   :alt-names   (:name funder-doc)
   :uri         (-> funder-doc :doi doi-id/to-long-doi-uri)
   :replaces    (:replaces funder-doc)
   :replaced-by (:replaced-by funder-doc)
   :tokens      (:token funder-doc)})

(defn ->extended-response-doc [funder-doc]
  (let [funder-doi (:doi funder-doc)]
    (merge
     (->response-doc funder-doc)
     {:work-count            (fetch-work-count funder-doi)
      :descendant-work-count (fetch-descendant-work-count funder-doi)
      :descendants           (:descendant funder-doc)
      :hierarchy             (:hierarchy funder-doc)
      :hierarchy-names       (:hierarchy-names funder-doc)})))

(defn fetch-one [query-context]
  (when-let [funder-doc (-> (elastic/request
                             (conf/get-service :elastic)
                             (query/->es-request query-context
                                                 :id-field :doi
                                                 :index "funder"))
                            (get-in [:body :hits :hits])
                            first
                            :_source)]
    (r/api-response
     :funder
     :content
     (->extended-response-doc funder-doc))))

;; todo level sort
(defn fetch
  "Search for funders by name tokens. Results are sorted by level within organizational
   hierarchy."
  [query-context]
  (let [es-request (query/->es-request
                    (query/prefix-query-context query-context :primary-name)
                    :index "funder")
        response (elastic/request (conf/get-service :elastic) es-request)
        docs (->> (get-in response [:body :hits :hits]) (map :_source))]
    (-> (r/api-response :funder-list)
        (r/with-query-context-info query-context)
        (r/with-debug-info query-context es-request)
        (r/with-result-items
          (get-in response [:body :hits :total])
          (map ->response-doc docs)))))

;; todo currently won't work due to filter.clj compounds only accepting
;;      one value per filter name 
(defn fetch-works [query-context]
  (let [funder-doi (:id query-context)
        filter-dois (conj (fetch-descendant-dois funder-doi) funder-doi)]
    (work/fetch
     (-> query-context
         (assoc :filter {"funder" {"doi" filter-dois}})))))
