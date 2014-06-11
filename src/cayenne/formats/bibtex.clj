(ns cayenne.formats.bibtex
  (:require [cayenne.ids.doi :as doi-id]
            [cayenne.ids.type :as type-id]
            [cayenne.latex :as latex]
            [clojure.string :as string])
  (:import [org.jbibtex BibTeXEntry BibTeXFormatter BibTeXDatabase Key DigitStringValue StringValue 
            StringValue$Style LaTeXString LaTeXPrinter]
           [java.io StringWriter]))

(def bibtex-entry-type
  {:journal-article BibTeXEntry/TYPE_ARTICLE
   :journal-issue BibTeXEntry/TYPE_MISC
   :journal-volume BibTeXEntry/TYPE_MISC
   :journal BibTeXEntry/TYPE_MISC
   :proceedings-article BibTeXEntry/TYPE_INPROCEEDINGS
   :proceedings BibTeXEntry/TYPE_MISC
   :dataset BibTeXEntry/TYPE_MISC
   :component BibTeXEntry/TYPE_MISC
   :report BibTeXEntry/TYPE_MISC
   :report-series BibTeXEntry/TYPE_MISC
   :standard BibTeXEntry/TYPE_MISC
   :standard-series BibTeXEntry/TYPE_MISC
   :edited-book BibTeXEntry/TYPE_BOOK
   :monograph BibTeXEntry/TYPE_BOOK
   :reference-book BibTeXEntry/TYPE_BOOK
   :book BibTeXEntry/TYPE_BOOK
   :book-series BibTeXEntry/TYPE_MISC
   :book-set BibTeXEntry/TYPE_MISC
   :dissertation BibTeXEntry/TYPE_PHDTHESIS
   :other BibTeXEntry/TYPE_MISC})

(defn ->bibtex-type [metadata]
  (cond (and
         (some #{(:type metadata)} [:book-chapter :book-section :book-part :book-track :book-entry])
         (empty? (:title metadata)))
        BibTeXEntry/TYPE_INBOOK
        (and
         (some #{(:type metadata)} [:book-chapter :book-section :book-part :book-track :book-entry])
         (not (empty? (:title metadata))))
        BibTeXEntry/TYPE_INCOLLECTION
        :else
        (bibtex-entry-type (:type metadata))))

(defn ->bibtex-key [metadata]
  (let [family (get-in metadata [:author 0 :family])
        year (get-in metadata [:issued :date-parts 0 0])]
    (-> (cond
         (and family year)
         (str family "_" year)
         family
         family
         year
         (str year)
         :else
         "1")
        (string/replace #"[^\+a-zA-Z0-9_]+" "_")
        (Key.))))

(defn braced-str [s]
  (StringValue. (latex/->latex-str (str s)) StringValue$Style/BRACED))

(defn add-field [entry metadata key metadata-lookup-fn]
  (when-let [metadata-value (metadata-lookup-fn metadata)]
    (.addField entry key (braced-str metadata-value)))
  entry)

(defn add-clean-field [entry metadata key metadata-lookup-fn]
  (when-let [metadata-value (metadata-lookup-fn metadata)]
    (.addField entry key (StringValue. (str metadata-value) StringValue$Style/BRACED)))
  entry)

(defn add-int-field [entry metadata key metadata-lookup-fn]
  (when-let [metadata-value (metadata-lookup-fn metadata)]
    (.addField entry key (DigitStringValue. (str metadata-value))))
  entry)

(def bibtex-month ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"])

(defn add-month [entry metadata]
  (when-let [month (-> metadata :issued :date-parts first second)]
    (.addField entry BibTeXEntry/KEY_MONTH (braced-str (get bibtex-month (dec month)))))
  entry)

;; todo add 'series' field, book container-title, when available
(defn add-titles [entry metadata]
  (cond
    (= :journal-article (:type metadata))
    (-> entry
        (add-field metadata BibTeXEntry/KEY_TITLE #(-> % :title first))
        (add-field metadata BibTeXEntry/KEY_JOURNAL #(-> % :container-title first)))
    (= :proceedings-article (:type metadata))
    (-> entry
        (add-field metadata BibTeXEntry/KEY_TITLE #(-> % :title first))
        (add-field metadata BibTeXEntry/KEY_BOOKTITLE #(-> % :container-title first)))
    (and (some #{(:type metadata)} [:book-chapter :book-section :book-part :book-track :book-entry])
         (not (empty? (:title metadata))))
    (-> entry
        (add-field metadata BibTeXEntry/KEY_TITLE #(-> % :title first))
        (add-field metadata BibTeXEntry/KEY_BOOKTITLE #(-> % :container-title first)))
    (and (some #{(:type metadata)} [:book-chapter :book-section :book-part :book-track :book-entry])
         (empty? (:title metadata)))
    (-> entry
        (add-field metadata BibTeXEntry/KEY_TITLE #(-> % :container-title first)))
    :else
    (-> entry
        (add-field metadata BibTeXEntry/KEY_TITLE #(-> % :title first)))))

(defn add-contributors [entry metadata entry-key metadata-field]
  (let [contribs (->> (get metadata metadata-field)
                     (map #(string/join " " [(:given %) (:family %)]))
                     (string/join " and ")
                     string/trim)]
    (when-not (string/blank? contribs)
      (.addField entry entry-key (braced-str contribs)))
    entry))

(defn ->bibtex-entry [metadata]
  (let [entry (BibTeXEntry. (->bibtex-type metadata) (->bibtex-key metadata))]
    (-> entry
        (add-clean-field metadata BibTeXEntry/KEY_DOI :DOI)
        (add-clean-field metadata BibTeXEntry/KEY_URL :URL)
        (add-int-field metadata BibTeXEntry/KEY_YEAR #(-> % :issued :date-parts first first))
        (add-month metadata)
        (add-field metadata BibTeXEntry/KEY_PUBLISHER :publisher)
        (add-field metadata BibTeXEntry/KEY_VOLUME :volume)
        (add-field metadata BibTeXEntry/KEY_NUMBER :issue)
        (add-field metadata BibTeXEntry/KEY_PAGES :page)
        (add-contributors metadata BibTeXEntry/KEY_AUTHOR :author)
        (add-contributors metadata BibTeXEntry/KEY_EDITOR :editor)
        (add-titles metadata))))

(defn ->bibtex [metadata]
  (let [db (doto (BibTeXDatabase.) (.addObject (->bibtex-entry metadata)))
        formatter (BibTeXFormatter.)
        writer (StringWriter.)]
    (.format formatter db writer)
    (.toString writer)))

