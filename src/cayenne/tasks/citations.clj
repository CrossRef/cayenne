(ns cayenne.tasks.citations
  (:import [java.io PrintWriter])
  (:require [clojure.java.io :as io]))

(defn match-citations [item matcher]
  (if-let [citations (get-in item [:rel :citation])]
    ())
  (if-let [children (get-in item [:rel :component])]
    (doseq [child children]
      (match-citations child matcher))))

(defn matching-citation-finder 
  "Count unstructured citations matching a particular regex pattern. Expects
   input in the form produced by unixref-citation-parser."
  [log-file matcher]
  (let [miss-count (atom 0)
        hit-count (atom 0)
        wrtr (PrintWriter. (io/writer log-file))]
    (fn [citations]
      (doseq [citation citations]
        (if (re-find matcher)
          (do 
            (swap! hit-count inc)
            (.println wrtr (str @hit-count \tab @miss-count \tab citation)) )
          (swap! miss-count inc))))))

