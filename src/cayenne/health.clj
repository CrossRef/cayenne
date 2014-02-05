(ns cayenne.health
  (:require [cayenne.item-tree :as i]
            [clojure.string :as string]))

;; check item tree health

;; check functions are backwards. they return *something* (ideally
;; context of the failure) if the check fails, otherwise they return 
;; *nil*

(defn title-case [item]
  (filter
   #(= (string/upper-case (:value %))
       (:value %))
   (i/find-items-of-type item :title)))

(defn full-publication-date [item]
  (let [pub-dates (concat
                   (i/get-tree-rel item :published-online)
                   (i/get-tree-rel item :published-print))]
    (filter
     #(or (nil? (:day %)) (nil? (:month %)) (nil? (:year %)))
     pub-dates)))

(defn funding-information [item]
  (not (empty? (i/get-tree-rel item :funder))))

(defn journal-has-issn [item]
  (when-let [journal (i/find-item-of-subtype :journal)]
    (when (empty? (i/get-item-ids journal :issn))
      journal)))

(defn journal-has-volume [item]
  (when-let [journal (i/find-item-of-subtype :journal)]
    (not (empty? (i/find-item-of-subtype :journal-volume)))))

(defn journal-has-issue [item]
  (when-let [journal (i/find-item-of-subtype :journal)]
    (not (empty? (i/find-item-of-subtype :journal-issue)))))

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

(defn contributors-have-full-names [item]
  (let [contributors (i/find-items-of-type item :person)]
    (filter
     #(or (nil? (:first-name %)) (nil? (:last-name %)))
     contributors)))

(defn contributors-have-orcids [item]
  (let [contributors (i/find-items-of-type item :person)]
    (filter
     #(empty? (i/get-item-ids % :orcid))
     contributors)))

(defn contributors-have-affiliations [item]
  (let [contributors (i/find-items-of-type item :person)]
    (filter #(nil? (:affiliation %)) contributors)))

(defn contributors-no-bad-punctuation [item]
  (let [contributors (i/find-items-of-type item :person)
        bad-punctuation #"[,;\:\"'><@&]"]
    (filter #(or (re-find bad-punctuation (:first-name %))
                 (re-find bad-punctuation (:last-name %)))
            contributors)))

(defn contributor-family-names-no-whitespace [item]
  (let [contributors (i/find-items-of-type item :person)
        family-names (map :last-name contributors)]
    (filter #(re-find #"\s" %) family-names)))

(defn articles-have-pages [item]
  (let [articles
        (concat
         (i/find-items-of-subtype item :journal-article)
         (i/find-items-of-subtype item :proceedings-article))]
    (filter
     #(or (nil? (:first-page %)) (nil? (:last-page %)))
     articles)))

(defn articles-have-separate-pages [item]
  (let [page-separator #"-"
        articles
        (concat
         (i/find-items-of-subtype item :journal-article)
         (i/find-items-of-subtype item :proceedings-article))]
    (filter
     #(or (re-find page-separator (:first-page %))
          (re-find page-separator (:last-page %)))
     articles)))

(def checks
  [{:id :misc.funding-information
    :description "Some funding information should be listed"
    :fn funding-information}
   {:id :misc.standard-case-titles
    :description "Titles should not be all upper-case"
    :fn title-case}
   {:id :article.has-pages
    :description "Articles should have first and last page numbers"
    :fn articles-have-pages}
   {:id :article.has-separate-pages
    :description "Article first and last page numbers should be separated"
    :fn articles-have-separate-pages}
   {:id :journal.has-issn
    :description "Journals should have at least one ISSN"
    :fn journal-has-issn}
   {:id :journal.has-volume
    :description "Journals should have a volume number"
    :fn journal-has-volume}
   {:id :journal.has-issue
    :description "Journals should have an issue number"
    :fn journal-has-issue}
   {:id :misc.full-publication-dates
    :description "Publication dates should have a day, month and year"
    :fn full-publication-date}
   {:id :funder.has-doi
    :description "Funders should have FundRef Funder DOIs"
    :fn funders-have-ids}
   {:id :funder.has-award
    :description "Funders should have at least one award"
    :fn funders-have-awards}
   {:id :contributor.has-full-name
    :description "Contributors should have a given and family name"
    :fn contributors-have-full-names}
   {:id :contributor.has-orcid
    :description "Contributors should have an ORCID"
    :fn contributors-have-orcids}
   {:id :contributor.has-affiliation
    :description "Contributors should have an affiliation"
    :fn contributors-have-affiliations}
   {:id :contributor.no-punctuation
    :description "Contributors should have names without needless punctuation"
    :fn contributors-no-bad-punctuation}
   {:id :contributor.single-family-name
    :description "Contributor family names should not contain whitespace"
    :fn contributor-family-names-no-whitespace}])

(defn check-tree 
  "Returns a report of checks performed against an item tree."
  ([item check]
     (let [result ((:fn check) item)
           pass (or (nil? result) (empty? result))
           check-to-merge (dissoc check :fn)]
       (if pass
         (assoc check-to-merge :pass true)
         (merge check-to-merge {:pass false :failures result}))))
  ([item]
     (map #(check-tree item %) checks)))
  
