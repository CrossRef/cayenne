(ns cayenne.elastic.convert-regression-test
  "Regression tests for the cayenne cayenne.elastic.conversion from Item Tree
   to Elastic Search JSON.
   Although this uses the EDN files produced by cayenne.formats.parser-regression-test,
   it doesn't directly use the output files. Instead a separate suite of EDN files is
   kept. To create new test cases, they should be manually copied in there.
   This ensures concerns remain separated."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [cayenne.elastic.convert :as convert]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [cayenne.item-tree :as itree]
            [clojure.data.json :as json]
            [clj-time.core :as clj-time]
            [cheshire.core :refer [generate-stream]])
  (:import [java.io PushbackReader]))

(def base (io/file "dev-resources/convert-regression"))

; Results are time-dependent, so freeze the time when generating and comparing result files.
(def now (clj-time/date-time 2019 1 1))

(defn itemtree->es-filename
  "Produce an Elastic Search JSON filename from an Item Tree EDN filename."
  [file]
  (io/file (.getParent file) (.replaceAll (.getName file) "\\.itemtree.edn" ".es.json")))

(defn input-result-files
  "Given a directory of EDN files, return a sequence of [edn file, json output file]."
  []
  (keep
   (fn [edn-file]
     (let [json-file (itemtree->es-filename edn-file)]
       (when (-> edn-file .getName (.endsWith ".itemtree.edn"))
         [edn-file json-file])))
   (file-seq base)))

(defn missing-result-files
  "Sequence of [edn file, json files] when the output file doesn't exist."
  []
  (->> (input-result-files)
       (remove #(-> % second .exists))))

(deftest ^:unit  all-inputs-should-have-outputs
  ; Make sure no data is left behind to become irrelevant.
  (testing "Every input EDN file should a corresponding JSON output file."
             (is (empty? (missing-result-files))
                 (str "Found orphaned Item Tree files without Elastic JSON files. Consider running generate-result-files."))))

(defn generate-result-files
  "Manual function used when writing tests.
   Scan inputs and generate result. These are then checked in and
   used for future regression tests.
   You should review the resulting files for correctness!"
  []
  (clj-time/do-at now
   (let [missing-files (missing-result-files)]
     (println "Found" (count missing-files) "missing files to generate.")
     (doseq [[in-file out-file] missing-files]
       (with-open [input (-> in-file io/reader PushbackReader.)]
         ; EDN file is sequence of pairs of [doi, itree]
         (let [result (map (fn [[doi itree]]
                             ; The item tree is for the whole XML document.
                             ; What gets indexed is the Work DOI.
                             ; The equivalent happens in cayenne.api.v1.feed
                             (convert/item->es-doc (itree/centre-on doi itree)))
                           (edn/read input))]

           (when result
             ; Only create the file if there was an output.
             ; This will avoid creating empty files which will cause errors later.
             ; Better to have the absence of the file caught by all-inputs-should-have-outputs.
             (with-open [wrtr (io/writer out-file)]
               ; Using Cheshire JSON for pretty printing, allowing easier verification of results.
               (generate-stream result wrtr {:pretty true})))))))
   (println "IMPORTANT! Manually verify that output files are correct!")))


(deftest ^:unit inputs-should-parse-expected
  (testing "Every input file should convert to a known result"
           (clj-time/do-at now
           (doseq [[in-file expected-result-file] (input-result-files)]
             (println "Compare" in-file)
             (with-open [input (-> in-file io/reader PushbackReader.)]
               ; EDN file is sequence of pairs of [doi, itree]
               (let [result (map (fn [[doi itree]]
                                   ; The item tree is for the whole XML document input.
                                   ; What gets indexed is the Work DOI.
                                   ; The equivalent happens in cayenne.api.v1.feed
                                   (-> doi
                                       (itree/centre-on itree)
                                       convert/item->es-doc
                                       ; Convert to and from JSON to ensure that any serialization
                                       ; occurs and we compare apples to apples.
                                       ; It will be converted to JSON in Elastic so this is a fair test.
                                       json/write-str
                                       (json/read-str :key-fn keyword)))
                                 (edn/read input))]

                 (is (.exists expected-result-file)
                     "Expected result file should exist.")

                   (with-open [expected-result-rdr (-> expected-result-file
                                                   io/reader)]
                     (is (= (json/read expected-result-rdr :key-fn keyword) result)))))))))