(ns cayenne.data.quality
  (:require [cayenne.item-tree :as i]
            [cayenne.api.v1.response :as r]
            [cayenne.action :as action]
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
    (remove
     #(and (:day %) (:month %) (:year %))
     pub-dates)))

(defn funding-information [item]
  (empty? (i/get-tree-rel item :funder)))

(defn citations [item]
  (empty? (i/get-tree-rel item :citation)))

(defn journal-has-issn [item]
  (when-let [journal (i/find-item-of-subtype item :journal)]
    (when (empty? (i/get-item-ids journal :issn))
      journal)))

(defn journal-has-volume [item]
  (when-let [journal (i/find-item-of-subtype item :journal)]
    (not (i/find-item-of-subtype item :journal-volume))))

(defn journal-has-issue [item]
  (when-let [journal (i/find-item-of-subtype item :journal)]
    (not (i/find-item-of-subtype item :journal-issue))))

(defn funders-have-ids [item]
  (let [funders (i/get-tree-rel item :funder)]
    (filter
     #(empty? (i/get-item-ids % :long-doi))
     funders)))

(defn funders-have-awards [item]
  (let [funders (i/get-tree-rel item :funder)]
    (filter
     #(empty? (i/get-item-rel % :awarded))
     funders)))

(defn contributors-have-full-names [item]
  (let [contributors (i/find-items-of-type item :person)]
    (remove
     #(and (:first-name %) (:last-name %))
     contributors)))

(defn contributors-have-orcids [item]
  (let [contributors (i/find-items-of-type item :person)]
    (filter
     #(empty? (i/get-item-ids % :orcid))
     contributors)))

(defn contributors-have-affiliations [item]
  (let [contributors (i/find-items-of-type item :person)]
    (remove :affiliation contributors)))

(defn contributors-no-bad-punctuation [item]
  (let [contributors (i/find-items-of-type item :person)
        bad-punctuation #"[;\:\"><@&\^%*]"]
    (filter #(or (re-find bad-punctuation (:first-name %))
                 (re-find bad-punctuation (:last-name %)))
            contributors)))

(defn articles-have-pages [item]
  (let [articles
        (concat
         (i/find-items-of-subtype item :journal-article)
         (i/find-items-of-subtype item :proceedings-article))]
    (remove
     #(and (:first-page %) (:last-page %))
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
   {:id :misc.citations
    :description "Some citations should be listed"
    :fn citations}
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
    :description "Contributors should have names without erroneous punctuation"
    :fn contributors-no-bad-punctuation}])

(defn passed? [result]
  (cond
   (true? result) false
   (false? result) true
   (seq result) false
   (not (seq result)) true))

(defn check-tree 
  "Returns a report of checks performed against an item tree."
  ([item check]
     (let [result ((:fn check) item)
           pass (passed? result)
           check-to-merge (dissoc check :fn)]
       (if pass
         (assoc check-to-merge :pass true)
         (merge check-to-merge {:pass false}))))
  ([item]
     (map #(check-tree item %) checks)))
  
(defn get-unixsd [doi]
  (let [record (promise)]
    (action/parse-doi doi (action/return-item record))
    (second @record)))

(defn fetch-quality
  [doi]
  (let [item-tree (get-unixsd doi)]
    (r/api-response :work-quality :content (check-tree item-tree))))
