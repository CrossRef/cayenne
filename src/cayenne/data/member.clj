(ns cayenne.data.member
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.facet :as facet]
            [cayenne.api.v1.filter :as filter]
            [cayenne.data.prefix :as prefix]
            [cayenne.data.work :as work]
            [cayenne.data.coverage :as coverage]
            [cayenne.ids.member :as member-id]
            [cayenne.ids.prefix :as prefix-id]
            [clojure.string :as string]
            [clj-time.coerce :as dc]
            [qbits.spandex :as elastic]))

(defn ->response-doc [member-doc & {:keys [coverage-doc]}]
  (cond-> {:id           (:id member-doc)
           :primary-name (:primary-name member-doc)
           :names        (->> (:prefix member-doc)
                              (map (comp string/trim :name))
                              (cons (:primary-name member-doc))
                              distinct)
           :prefixes     (map :value (:prefix member-doc))
           :prefix       (map #(select-keys % [:reference-visibility :public-references :name :value]) (:prefix member-doc))
           :location     (-> member-doc :location string/trim)
           :tokens       (:token member-doc)}
    (not (nil? coverage-doc))
    (merge
     (merge-with
      merge
      (coverage/coverage coverage-doc :current)
      (coverage/coverage coverage-doc :backfile))
     {:breakdowns             (get-in coverage-doc [:breakdowns :breakdowns])
      :counts                 (select-keys coverage-doc [:current-dois
                                                         :backfile-dois
                                                         :total-dois])
      :coverage-type          (coverage/coverage-type coverage-doc)
      :counts-type            (coverage/type-counts coverage-doc)
      :last-status-check-time (-> coverage-doc :finished dc/to-long)})))

(defn get-coverage [subject-type subject-ids]
  (let [query (-> {}
                  (assoc-in [:query :bool :must]
                            [{:term {:subject-type subject-type}}])
                  (assoc-in [:query :bool :minimum_should_match] 1)
                  (assoc-in [:query :bool :should]
                            (map (fn [subject-id]
                                   {:term {:subject-id subject-id}}) subject-ids))
                  (assoc :size (count subject-ids))
                  (assoc :sort {:finished :desc}))]
    (-> (elastic/request
         (conf/get-service :elastic)
         {:method :get
          :url "/coverage/coverage/_search"
          :body query})
        (get-in [:body :hits :hits])
        (->> (map :_source)))))

(defn fetch-one [query-context]
  (let [response (elastic/request
                  (conf/get-service :elastic)
                  (query/->es-request query-context
                                      :id-field :id
                                      :index "member"))]
    (when-let [member-doc (-> response
                              (get-in [:body :hits :hits])
                              first
                              :_source)]
      (r/api-response :member
                      :content
                      (->response-doc member-doc
                                      :coverage-doc
                                      (first (get-coverage :member [(:id member-doc)])))))))

(defn fetch-works [query-context]
  (work/fetch query-context :id-field :member-id))

(defn fetch [query-context]
  (let [es-request (query/->es-request (query/prefix-query-context
                                        query-context
                                        :primary-name)
                                       :index "member"
                                       :filters filter/member-filters)
        response (elastic/request
                  (conf/get-service :elastic)
                  es-request)
        docs (->> [:body :hits :hits]
                  (get-in response)
                  (map :_source))
        find-coverage (->> docs
                           (map :id)
                           (get-coverage :member)
                           (partial (fn [coverages id]
                                      (some
                                       #(if (= (:subject-id %) id) %) coverages))))]
    (-> (r/api-response :member-list)
        (r/with-query-context-info query-context)
        (r/with-debug-info query-context es-request)
        (r/with-result-items
          (get-in response [:body :hits :total])
          (map #(->response-doc
                 %
                 :coverage-doc (find-coverage (:id %))) docs)))))
