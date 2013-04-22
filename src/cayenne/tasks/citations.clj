(ns cayenne.tasks.citations
  (:import [java.io PrintWriter])
  (:require [clojure.java.io :as io]
            [cayenne.conf :as conf]))

(defn matching-citation-finder
  "Count unstructured citations matching a particular regex pattern. Expects
   input in the form produced by unixref-citation-parser."
  [log-file patt]
  (conf/set-result! :hit-count 0)
  (conf/set-result! :miss-count 0)
  (let [wrtr (PrintWriter. (io/writer log-file))]
    (fn [citations]
      (doseq [citation citations]
        (when-let [citation-text (:unstructured citation)]
          (if (re-find patt (.trim citation-text))
            (do
              (conf/update-result! :hit-count inc)
              (.println wrtr (str citation-text))
              (.flush wrtr))
            (conf/update-result! :miss-count inc)))))))

