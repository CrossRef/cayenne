(ns cayenne.api.response)

(defn with-page-info [response start-page per-page]
  (-> response
      ;(assoc-in [:message :startIndex] (* (- start-page 1) * per-page))
      (assoc-in [:message :itemsPerPage] per-page)
      (assoc-in [:message :query :startPage] start-page)))

(defn with-query-terms-info [response terms]
  (assoc-in response [:message :query :searchTerms] terms))

(defn with-query-context-info [response context]
  (-> response
      (with-page-info (:page context) (:rows context))
      (with-query-terms-info (:terms context))))

(defn with-result-items [response total items]
  (-> response
      (assoc-in [:message :totalResults] total)
      (assoc-in [:message :items] items)))

(defn api-response [type & {:keys [content] :or {:content {}}}]
  {:status :ok
   :messageType type
   :message content})

