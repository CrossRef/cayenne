(ns cayenne.health
  (:require [cayenne.item-tree :as i]
            [clojure.string :as string]))

;; check item tree health

(defn title-case [item]
  (filter
   #(= (string/capitalise (:value %))
       (:value %))
   (i/find-items-of-type item :title)))

(defn journal-has-issn [item]
  (when-let [journal (i/find-item-of-subtype :journal)]
    (when (empty? (i/get-item-ids journal :issn))
      journal)))

(defn full-publication-date [item]
  (let [pub-dates (concat
                   (i/get-tree-rel item :published-online)
                   (i/get-tree-rel item :published-print))]
    (filter
     #(or (nil? (:day %)) (nil? (:month %)) (nil? (:year %)))
     pub-dates)))

(defn funders-have-ids [item]
  (let [funders (i/get-tree-rel item :funder)]
    (filter
     #(empty? (i/get-item-ids % :long-doi))
     funders)))

(defn funders-have-awards [item]
  (let [funders (i/get-tree-rel item :funder)]
    (filter
     #(empty? (i/get-item-rel item :awarded))
     funders)))

(defn full-contributor-names [item]
  ())

(def checks
  [{:id :general.standard-case-titles
    :description "Titles should not be all upper-case"
    :fn title-case}
   {:id :journal.has-issn
    :description "Journals should have at least one ISSN"
    :fn journal-has-issn}
   {:id :general.full-publication-dates
    :description "Publication dates should have a day, month and year"
    :fn full-publication-date}
   {:id :funder.has-id
    :description "Funders should have FundRef IDs"
    :fn funders-have-ids}
   {:id :funder.has-award
    :description "Funders should have at least one award"
    :fn funders-have-awards}
   {:id :contributor.full-name
    :description "Contributors should have a given and family name"
    :fn full-contributor-names}])

(defn check-tree 
  "Returns a report of checks performed against an item tree."
  ([item check]
     (let [result ((:fn check) item)
           pass (or (nil? result) (empty? result))]
       (if pass
         (assoc check :pass true)
         (merge check {:pass false :failures result}))))
  ([item]
     (map #(check-tree item %) checks)))
  
