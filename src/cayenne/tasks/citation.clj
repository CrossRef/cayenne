(ns cayenne.tasks.citation
  (:import [java.io StringWriter PrintWriter])
  (:use [cayenne.util])
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.string :as string]
            [cayenne.item-tree :as itree]
            [cayenne.url :as url]
            [cayenne.conf :as conf]
            [cayenne.tasks.category :as cat]
            [cayenne.ids.issn :as issn]
            [somnium.congomongo :as m]))

(def hit-count (atom 0))
(def miss-count (atom 0))
(def citations-scanned (atom 0))
(def records-scanned (atom 0))

(defn matching-citation-finder
  "Count unstructured citations matching a particular regex pattern. Expects
   input in the form produced by unixref-citation-parser."
  [log-file patt]
  (reset! hit-count 0)
  (reset! miss-count 0) 
  (let [wrtr (PrintWriter. (io/writer log-file))]
    (fn [citations]
      (doseq [citation citations]
        (when-let [citation-text (:unstructured citation)]
          (if (re-find patt (.trim citation-text))
            (do
              (swap! hit-count inc)
              (.println wrtr (str citation-text))
              (.flush wrtr))
            (swap! miss-count inc)))))))

(defn to-csv-line [categories year citation url]
  (if url
    [(nth categories 0 nil)
     (nth categories 1 nil)
     (nth categories 2 nil)
     (nth categories 3 nil)
     (nth categories 4 nil)
     (nth categories 5 nil)
     year
     true
     (:valid url)
     (:resolves url)
     (:root url)
     (:tld url)
     (:url url)]
    [(nth categories 0 nil)
     (nth categories 1 nil)
     (nth categories 2 nil)
     (nth categories 3 nil)
     (nth categories 4 nil)
     (nth categories 5 nil)
     year
     false
     false
     false
     nil
     nil
     nil]))

(defn to-short-csv-line [citing-doi url]
  (if (:url url)
    [citing-doi             ;; Citing DOI
     (:year url)            ;; Citing DOI year of publication
     true                   ;; Has unstructured text?
     true                   ;; Has URI?
     (:valid url)           ;; Has valid URI?
     (:root url)            ;; URI root
     (:tld url)             ;; URI tld
     (:url url)             ;; URI
     (:original-text url)]  ;; Original citation text
    [citing-doi                            ;; Citing DOI
     (:year url)                           ;; Citing DOI year of publication
     (if (:original-text url) true false)  ;; Has unstructured text?
     false                                 ;; Has URI?
     false                                 ;; Has valid URI?
     nil                                   ;; URI root
     nil                                   ;; URI tld
     nil                                   ;; URI
     (:original-text url)]))               ;; Original citation text

(defn simple-url-citation-checker
  "Like the full url citation checker, except does not try to resolve
   URL and does not consider citing DOI's ISSN and science categories."
  [out-file]
  (reset! citations-scanned 0)
  (reset! records-scanned 0)
  (let [wrtr (conf/file-writer out-file)]
    (fn [item]
      (swap! records-scanned inc) 
      (let [article (itree/find-item-of-subtype (second item) :journal-article)
            citations (itree/get-item-rel article :citation)
            doi (first (itree/get-item-ids article :long-doi))
            year (-> (concat (get-in article [:rel :published-online])
                             (get-in article [:rel :published-print]))
                     first
                     :year)]
        (when citations
          (swap! citations-scanned + (count citations))
          (let [lines (->> citations
                           (map :unstructured)
                           (map #(merge {:original-text % :year year}
                                        (if %
                                          (url/locate-without-resolve %)
                                          {})))
                           (map (partial to-short-csv-line doi)))]
            (with-open [str-wrtr (StringWriter.)]
              (csv/write-csv str-wrtr lines)
              (conf/write-to wrtr (.toString str-wrtr)))))))))
  
(defn full-url-citation-checker
  "Finds URLs in citations. Will pull out year and science categories
   of the citing work if available. Finally will try to resolve
   any extracted URLs."
  [out-file]
  (reset! citations-scanned 0)
  (reset! records-scanned 0)
  (let [wrtr (conf/file-writer out-file)]
    (fn [item]
      (let [journal (itree/find-item-of-subtype (second item) :journal)
            article (itree/find-item-of-subtype (second item) :journal-article)
            year (:year (first (or 
                                (get-in article [:rel :published-online])
                                (get-in article [:rel :published-print]))))
            issn (issn/normalize-issn 
                  (first 
                   (filter (comp issn/is-issn? issn/normalize-issn) (:id journal))))
            categories (cat/get-issn-categories-memo issn)
            citations (get-in article [:rel :citation])
            citation-texts (map :unstructured (filter :unstructured citations))
            urls (map url/locate citation-texts)]
        (with-open [str-wrtr (StringWriter.)]
          (csv/write-csv str-wrtr (map (partial to-csv-line categories year) citation-texts urls))
          (conf/write-to wrtr (.toString str-wrtr)))))))


