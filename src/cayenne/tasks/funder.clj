(ns cayenne.tasks.funder
  (:require [somnium.congomongo :as m]
            [clojure.core.memoize :as memoize]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [cayenne.item-tree :as itree]
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
  
(defn load-funders []
  (ensure-funder-indexes!)
  (with-open [rdr (io/reader (conf/get-resource :funders))]
    (let [funders (csv/read-csv rdr :separator \tab)]
      (doseq [funder funders]
        (let [name-type (if (= (nth funder 2) "N") :primary :other)]
          (insert-funder (first funder)
                         (second funder)
                         name-type))))))

(defn get-funder-names [funder-uri]
  (m/with-mongo (conf/get-service :mongo)
    (let [funder (m/fetch-one :funders :where {:uri funder-uri})]
      (conj (or (:other_names_display funder) []) (:primary_name_display funder)))))

(defn get-funder-primary-name [funder-uri]
  (m/with-mongo (conf/get-service :mongo)
    (:primary_name_display (m/fetch-one :funders :where {:uri funder-uri}))))

(def get-funder-names-memo (memoize/memo-lru get-funder-names))

(def get-funder-primary-name-memo (memoize/memo-lru get-funder-primary-name))

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
