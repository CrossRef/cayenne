(ns cayenne.data.prefix
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.query :as query]
            [cayenne.data.work :as work]
            [qbits.spandex :as elastic]))

(defn fetch-works [query-context]
  (work/fetch query-context :id-field :owner-prefix))

(defn fetch-one [query-context]
  (when-let [member-doc (-> (elastic/request
                             (conf/get-service :elastic)
                             (query/->es-request query-context
                                                 :id-field :prefix.value
                                                 :index "member"))
                            (get-in [:body :hits :hits])
                            first
                            :_source)]
    (let [prefix (->> member-doc
                      :prefix
                      (filter #(= (:value %) (:id query-context)))
                      first)]
      (r/api-response :prefix :content {:member            (:id member-doc)
                                        :name              (:name prefix)
                                        :public-references (:public-references prefix)
                                        :prefix            (:value prefix)}))))
