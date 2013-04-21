(ns cayenne.tasks.citations
  (:import [java.io PrintWriter])
  (:require [clojure.java.io :as io]))

(def hit-count (atom 0))
(def miss-count (atom 0))

(defn clear-citation-finder-counts []
  (swap! hit-count (constantly 0))
  (swap! miss-count (constantly 0)))

(defn matching-citation-finder
  "Count unstructured citations matching a particular regex pattern. Expects
   input in the form produced by unixref-citation-parser."
  [log-file patt]
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

