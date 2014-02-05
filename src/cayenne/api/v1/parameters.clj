(ns cayenne.api.v1.parameters
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(def valid-list-parameters (set [:sample :query :offset :rows :selector :filter]))
(def valid-singular-parameters (set [:selector]))
(def valid-basic-parameters (set []))

(defn get-parameters [request-context]
  (if (nil? (get-in request-context [:request :body]))
    (-> request-context
        (get-in [:request :params]))
    (-> request-context
        (get-in [:request :body])
        (io/reader)
        (json/read :key-fn keyword))))

(defn malformed-param-names? [valid-parameter-set request-context]
  (let [params (-> request-context get-parameters keys set)]
    (-> (clojure.set/difference params valid-parameter-set)
        (count)
        (zero?)
        (not))))

(defn malformed-basic-request?
  [request-context]
  (malformed-param-names? valid-basic-parameters request-context))

(defn malformed-list-request? 
  "Check query parameters and JSON body top-level keywords. If any parameter 
   is not in the allowed list of valid list resource parameters return true."
  [request-context]
  (malformed-param-names? valid-list-parameters request-context))

(defn malformed-singular-request?
  "Check query parameters and JSON body top-level keywords. If any parameter
   is not in the allowed list of valid singular resource parameters return
   true."
  [request-context]
  (malformed-param-names? valid-singular-parameters request-context))
