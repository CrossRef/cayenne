(ns cayenne.data.program
  (:require [cayenne.api.v1.response :as r]))

;; definition of deposit programs

(def programs
  [{:id :reindex}])
;   {:id :standard}
;   {:id :citations}])

(defn fetch-all []
  (-> (r/api-response :program-list)
      (r/with-result-items (count programs) programs)))
                  
  
