(ns cayenne.data.journal
  (:require [cayenne.conf :as conf]
            [cayenne.util :as util]
            [cayenne.data.work :as work]
            [cayenne.ids.issn :as issn-id]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [qbits.spandex :as elastic]
            [clj-time.coerce :as dc]
            [cayenne.api.v1.filter :as filter]))

(defn ->response-doc [journal-doc & {:keys [coverage-doc]}]
  (cond-> {:title (:title journal-doc)
           :publisher (:publisher journal-doc)
           :ISSN (map :value (:issn journal-doc))
           :issn-type (:issn journal-doc)
           :subjects (or (:subject journal-doc) [])} ;; todo {:ASJC :name}
    (not (nil? coverage-doc))
    (merge
     {:flags                  (get-in coverage-doc [:coverage :flags])
      :coverage               (get-in coverage-doc [:coverage :coverage])
      :breakdowns             (get-in coverage-doc [:breakdowns :breakdowns])
      :counts                 (select-keys coverage-doc [:current-dois
                                                         :backfile-dois
                                                         :total-dois])
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
  (when-let [journal-doc (-> (elastic/request
                              (conf/get-service :elastic)
                              (query/->es-request query-context
                                                  :id-field :issn.value
                                                  :index "journal"))
                             (get-in [:body :hits :hits])
                             first
                             :_source)]
    (r/api-response :journal
                    :content
                    (->response-doc journal-doc
                                    :coverage-doc
                                    (first (get-coverage :journal [(:id journal-doc)]))))))

(defn fetch [query-context]
  (let [es-request (query/->es-request (query/prefix-query-context
                                        query-context
                                        :title)
                                       :index "journal")
        response (elastic/request
                  (conf/get-service :elastic)
                  es-request)
        docs (->> [:body :hits :hits]
                  (get-in response)
                  (map :_source))
        find-coverage (->> docs
                           (map :id)
                           (get-coverage :journal)
                           (partial (fn [coverages id]
                                      (some
                                       #(if (= (:subject-id %) id) %) coverages))))]
    (-> (r/api-response :journal-list)
        (r/with-query-context-info query-context)
        (r/with-debug-info query-context es-request)
        (r/with-result-items
          (get-in response [:body :hits :total])
          (map #(->response-doc
                 %
                 :coverage-doc (find-coverage (:id %))) docs)))))

(defn fetch-works [query-context]
  (work/fetch query-context :id-field :issn.value))
