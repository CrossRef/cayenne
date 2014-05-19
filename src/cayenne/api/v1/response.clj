(ns cayenne.api.v1.response)

(defn with-page-info [response offset per-page]
  (-> response
      ;(assoc-in [:message :start-index] (* (- start-page 1) * per-page))
      (assoc-in [:message :items-per-page] per-page)
      (assoc-in [:message :query :start-index] offset)))

(defn with-query-terms-info [response terms]
  (assoc-in response [:message :query :search-terms] terms))

(defn with-query-context-info [response context]
  (-> response
      (with-page-info (:offset context) (:rows context))
      (with-query-terms-info (:terms context))))

(defn with-result-items [response total items]
  (-> response
      (assoc-in [:message :total-results] total)
      (assoc-in [:message :items] items)))

(defn with-result-facets [response facet-info]
  (-> response
      (assoc-in [:message :facets] facet-info)))

(defn api-response [type & {:keys [content] :or {:content {}}}]
  {:status :ok
   :message-type type
   :message-version "1.0.0"
   :message content})

