(ns cayenne.api.v1.response
  (:require [clojure.string :as string]
            [cayenne.util :refer [?>]]
            [cayenne.conf :as conf]))

(defn with-debug-info [response solr-response query-context es-body]
  (if-not (:debug query-context)
    response
    (-> response
        (assoc-in [:debug :instance-hostname]
                  (.getCanonicalHostName (java.net.InetAddress/getLocalHost)))
        (assoc-in [:debug :elastic-request-body] es-body)
        (assoc-in [:debug :elastic-client-config] (conf/get-param [:service :elastic]))
        (assoc-in [:debug :query-context] query-context))))

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

(defn with-result-items [response total items & {:keys [next-cursor]}]
  (-> response
      (?> (not (string/blank? next-cursor))
          assoc-in [:message :next-cursor] next-cursor)
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

