(ns cayenne.data.member
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.facet :as facet]
            [cayenne.api.v1.filter :as filter]
            [cayenne.data.prefix :as prefix]
            [cayenne.data.work :as work]
            [cayenne.ids.member :as member-id]
            [cayenne.ids.prefix :as prefix-id]
            [cayenne.formats.citeproc :as citeproc]
            [clojure.string :as string]
            [clj-time.coerce :as dc]
            [qbits.spandex :as elastic]))

(defn ->response-doc [member-doc & {:keys [coverage-doc]}]
  (cond-> {:id           (:id member-doc)
           :primary-name (:primary-name member-doc)
           :names        (map :name (:prefix member-doc))
           :prefixes     (map :value (:prefix member-doc))
           :prefix       (:prefix member-doc)
           :location     (:location member-doc)
           :tokens       (:token member-doc)}
    (not (nil? coverage-doc))
    (merge
     {:flags                  (get-in coverage-doc [:coverage :flags])
      :coverage               (get-in coverage-doc [:coverage :coverage])
      :counts                 (select-keys coverage-doc [:current-dois
                                                         :backfile-dois
                                                         :total-dois])
      :last-status-check-time (-> coverage-doc :finished dc/to-long)})))

(defn get-coverage [subject-type subject-id]
  (-> (elastic/request
       (conf/get-service :elastic)
       {:method :get
        :url "/coverage/coverage/_search"
        :body (-> {}
                  (assoc-in [:query :bool :must]
                            [{:term {:subject-type subject-type}}
                             {:term {:subject-id subject-id}}])
                  (assoc :size 1)
                  (assoc :sort {:finished :desc}))})
      (get-in [:body :hits :hits])
      first
      :_source))

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
                                      (get-coverage :member (:id member-doc)))))))

(defn fetch-works [query-context]
  (work/fetch query-context :id-field :member-id))

(defn prefix-query-context [query-context]
  (-> query-context
      (assoc :prefix-terms (:terms query-context))
      (assoc :prefix-field :primary-name)
      (dissoc :terms)))

(defn fetch [query-context]
  (let [es-request (query/->es-request (prefix-query-context query-context)
                                       :index "member"
                                       :filters filter/member-filters)
        response (elastic/request
                  (conf/get-service :elastic)
                  es-request)
        docs (->> (get-in response [:body :hits :hits])
                  (map :_source))]
    (-> (r/api-response :member-list)
        (r/with-query-context-info query-context)
        (r/with-debug-info query-context es-request)
        (r/with-result-items
          (get-in response [:body :hits :total])
          (map ->response-doc docs)))))
                        
