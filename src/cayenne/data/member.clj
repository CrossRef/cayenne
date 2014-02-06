(ns cayenne.data.member
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.filter :as filter]
            [cayenne.ids.member :as member-id]
            [somnium.congomongo :as m]
            [clojure.string :as string]))

(defn ->response-doc [pub-doc]
  {:id (:id pub-doc)
   :names (:names pub-doc)
   :prefixes (:prefixes pub-doc)
   :tokens (:tokens pub-doc)})

(defn parse-query-terms 
  "Split query terms."
  [terms]
  (when terms
    (-> terms
        (string/lower-case)
        (string/replace #"[,\.\-\'\"]" "")
        (string/split #"\s+"))))

(defn fetch-one [query-context]
  (let [pub-doc (m/with-mongo (conf/get-service :mongo)
                  (m/fetch-one 
                   "publishers"
                   :where {:id (-> query-context
                                   (:id)
                                   (member-id/extract-member-id)
                                   (Integer/parseInt))}))]
    (when pub-doc
      (r/api-response :member
                      :content
                      (->response-doc pub-doc)))))

(defn fetch [query-context]
  (let [parsed-terms (or (parse-query-terms (:terms query-context)) [])
        and-list (map #(hash-map "tokens" {"$regex" (str "^" %)}) parsed-terms)
        where-clause (if (empty? and-list) {} {"$and" and-list})
        mongo-query (query/->mongo-query query-context 
                                         :where where-clause
                                         :sort {:id 1})
        docs (if (and (:rows query-context) (zero? (:rows query-context)))
               []
               (m/with-mongo (conf/get-service :mongo)
                 (apply m/fetch "publishers" mongo-query)))
         result-count (m/with-mongo (conf/get-service :mongo)
                        (apply m/fetch-count "publishers" mongo-query))]
    (-> (r/api-response :member-list)
        (r/with-result-items result-count (map ->response-doc docs)))))
                        
