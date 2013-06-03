(ns cayenne.tasks.funder
  (:require [somnium.congomongo :as m]
            [clojure.core.memoize :as memoize]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [cayenne.item-tree :as itree]
            [cayenne.rdf :as rdf]
            [cayenne.conf :as conf]
            [cayenne.ids.fundref :as fundref]))

(defn ensure-funder-indexes! []
  (m/with-mongo (conf/get-service :mongo)
    (m/add-index! :funders [:name_tokens])
    (m/add-index! :funders [:id])
    (m/add-index! :funders [:uri])))

(defn simplyfy-name [name]
  (-> (.toLowerCase name)
      (.trim)
      (.replaceAll "," "")
      (.replaceAll "\\." "")
      (.replaceAll "'" "")
      (.replaceAll "\"" "")
      (.replaceAll "-" "")))

(defn tokenize-name [name]
  (string/split (simplyfy-name name) #"\s+"))

(defn add-tokens [existing tokens]
  (let [existing-tokens (or (:name_tokens existing) [])]
    (assoc existing :name_tokens (set (concat existing-tokens tokens)))))

(defn add-name [existing name name-type]
  (if (= name-type :primary)
    (-> existing
        (assoc :primary_name_display (.trim name))
        (add-tokens (tokenize-name name)))
    (let [other-names (or (:other_names existing) [])
          other-names-display (or (:other_names_display existing) [])]
      (-> existing
          (assoc :other_names_display (conj other-names-display (.trim name)))
          (add-tokens (tokenize-name name))))))

(defn insert-funder [id name name-type]
  (m/with-mongo (conf/get-service :mongo)
    (if-let [existing-doc (m/fetch-one :funders :where {:id id})]
      (m/update! :funders existing-doc (add-name existing-doc name name-type))
      (m/insert! :funders (add-name {:id id :uri (fundref/id-to-doi-uri id)} name name-type)))))

(defn insert-full-funder [id name alt-names parent-id child-ids affiliation-ids]
  (m/with-mongo (conf/get-service :mongo)
    (m/insert! :funderstest
               {:id id
                :uri (fundref/id-to-doi-uri id)
                :primary_name_display name
                :other_names_display alt-names
                :name_tokens (concat (tokenize-name name)
                                     (mapcat tokenize-name alt-names))
                :parent parent-id
                :children (or child-ids [])
                :affiliated (or affiliation-ids [])})))
  
(defn load-funders-csv []
  (ensure-funder-indexes!)
  (with-open [rdr (io/reader (conf/get-resource :funders))]
    (let [funders (csv/read-csv rdr :separator \tab)]
      (doseq [funder funders]
        (let [name-type (if (= (nth funder 2) "N") :primary :other)]
          (insert-funder (first funder)
                         (second funder)
                         name-type))))))

(defn find-funders [model]
  (-> (rdf/select model
                  :predicate (rdf/rdf model "type")
                  :object (rdf/skos-type model "Concept"))
      (rdf/subjects)))

(defn res->id [funder-concept-node]
  (when funder-concept-node
    (last (string/split (rdf/->uri funder-concept-node) #"/"))))

(def svf (partial rdf/get-property "http://www.elsevier.com/xml/schema/grant/grant-1.2/"))

(defn get-labels [model node kind]
  (->> (rdf/select model :subject node :predicate (rdf/skos-xl model kind))
       (rdf/objects)
       (mapcat #(rdf/select model :subject % :predicate (rdf/skos-xl model "literalForm")))
       (rdf/objects)
       (map #(.getString %))))
      
(defn funder-concept->map [model funder-concept-node]
  {:id (res->id funder-concept-node)
   :broader-id (-> (rdf/select model
                               :subject funder-concept-node
                               :predicate (rdf/skos model "broader"))
                   (rdf/objects)
                   (first)
                   (res->id))
   :narrower-ids (->> (rdf/select model
                                  :subject funder-concept-node
                                  :predicate (rdf/skos model "narrower"))
                      (rdf/objects)
                      (map res->id))
   :afilliated-ids (->> (rdf/select model
                                    :subject funder-concept-node
                                    :predicate (svf model "affilWith"))
                        (rdf/objects)
                        (map res->id))
   :name (first (get-labels model funder-concept-node "prefLabel"))
   :alternate-names (get-labels model funder-concept-node "altLabel")})

(defn load-funders-rdf [rdf-file]
  (ensure-funder-indexes!)
  (let [model (rdf/document->model rdf-file)]
    (doall
     (->> (find-funders model)
          (take 10)
          (map (partial funder-concept->map model))
          (map #(insert-full-funder
                 (:id %)
                 (:name %)
                 (:alternative-names %)
                 (:broader-id %)
                 (:narrower-ids %)
                 (:affiliated-ids %)))))))

(defn get-funder-names [funder-uri]
  (m/with-mongo (conf/get-service :mongo)
    (let [funder (m/fetch-one :funders :where {:uri funder-uri})]
      (conj (or (:other_names_display funder) []) 
            (:primary_name_display funder)))))

(defn get-funder-primary-name [funder-uri]
  (m/with-mongo (conf/get-service :mongo)
    (:primary_name_display (m/fetch-one :funders :where {:uri funder-uri}))))

(def get-funder-names-memo (memoize/memo-lru get-funder-names))

(def get-funder-primary-name-memo (memoize/memo-lru get-funder-primary-name))

(defn clear! []
  (memoize/memo-clear! get-funder-primary-name)
  (memoize/memo-clear! get-funder-names))

(defn canonicalize-funder-name
  [funder-item]
  (let [funder-uri (first (:id funder-item))]
    (if-let [canonical-name (get-funder-primary-name-memo funder-uri)]
      (merge funder-item {:name canonical-name :canonical true})
      funder-item)))

(defn apply-to 
  "If a funder specifies an ID, replace its publisher-provided name with our
   canonical primary name."
  ([item]
     (itree/update-tree-rel canonicalize-funder-name item :funder))
  ([id item]
     [id (apply-to item)]))
