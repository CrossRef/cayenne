(ns cayenne.data.funder
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.filter :as filter]
            [cayenne.formats.citeproc :as citeproc]
            [cayenne.ids.fundref :as fr-id]
            [somnium.congomongo :as m]
            [clojure.string :as string]))

(def solr-funder-id-field "funder_doi")

(defn get-solr-works [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context 
                                  :id-field solr-funder-id-field
                                  :filters filter/std-filters))
      (.getResults)))

(defn get-solr-work-count 
  "Get work count from solr for a mongo funder doc."
  [funder-doc]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query {:id (:uri funder-doc)}
                                  :id-field solr-funder-id-field
                                  :paged false
                                  :count-only true))
      (.getResults)
      (.getNumFound)))

(defn get-solr-descendant-work-count
  [funder-doc]
  (let [ids (vec (conj (map fr-id/id-to-doi-uri (:descendants funder-doc))
                  (:uri funder-doc)))]
    (-> (conf/get-service :solr)
        (.query (query/->solr-query {:id ids}
                                    :id-field solr-funder-id-field
                                    :paged false
                                    :count-only true))
        (.getResults)
        (.getNumFound))))

(defn ->short-id [funder-doc]
  (-> (:uri funder-doc) (string/split #"/") (last)))

(defn ->response-doc [funder-doc]
  {:id (->short-id funder-doc)
   :location (:country funder-doc)
   :name (:primary_name_display funder-doc)
   :alt-names (:other_names_display funder-doc)
   :uri (:uri funder-doc)
   :tokens (:name_tokens funder-doc)
   :work-count (get-solr-work-count funder-doc)
   :descendant-work-count (get-solr-descendant-work-count funder-doc)})

(defn ->hierarchy-doc [funder-doc]
  {:id (->short-id funder-doc)
   :uri (:uri funder-doc)
   :descendants (:descendants funder-doc)
   :hierarchy (:nesting funder-doc)
   :hierarchy-names (:nesting_names funder-doc)})

(defn fetch-one [query-context]
  (let [query {:id (-> query-context
                       (:id)
                       (fr-id/doi-uri-to-id))}
        funder-doc (m/with-mongo (conf/get-service :mongo)
                     (m/fetch-one "funders" :where query))]
    (when funder-doc
      (r/api-response :funder 
                      :content (->response-doc funder-doc)))))

(defn parse-query-terms 
  "Split query terms."
  [terms]
  (-> terms
      (string/lower-case)
      (string/replace #"[,\.\-\'\"]" "")
      (string/split #"\s+")))

(defn fetch
  "Search for funders by name tokens. Results are sorted by level within organizational
   hierarchy."
  [query-context]
  (let [parsed-terms (parse-query-terms (:terms query-context))
        and-list (map #(hash-map "name_tokens" {"$regex" (str "^" %)}) parsed-terms)
        mongo-query (query/->mongo-query query-context
                                         :where {"$and" and-list}
                                         :sort {:level 1})
        docs (m/with-mongo (conf/get-service :mongo)
               (apply m/fetch "funders" mongo-query))]
    (r/api-response :funder-list :content (map ->response-doc docs))))

(defn fetch-descendant-ids
  "Get all descendant funder ids for a funder."
  [query-context]
  (m/with-mongo (conf/get-service :mongo)
    (map fr-id/id-to-doi-uri
         (-> "funders"
             (m/fetch-one :where {:uri (:id query-context)})
             (:descendants)))))

(defn fetch-hierarchy
  [query-context]
  (let [funder (m/with-mongo (conf/get-service :mongo)
                 (m/fetch-one "funders" :where {:uri (:id query-context)}))]
    (r/api-response :funder-hierarchy :content (->hierarchy-doc funder))))

(defn fetch-works 
  "Return all the works related to a funder and its sub-organizations."
  [query-context]
  (let [descendant-ids (fetch-descendant-ids query-context)
        descendant-query (update-in query-context [:id] #(vec (conj descendant-ids %)))
        doc-list (get-solr-works descendant-query)]
    (-> (r/api-response :work-list)
        (r/with-query-context-info descendant-query)
        (r/with-result-items 
          (.getNumFound doc-list)
          (map citeproc/->citeproc doc-list)))))

