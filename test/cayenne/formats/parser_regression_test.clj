(ns cayenne.formats.parser-regression-test
  "Regression tests for the UNIXSD and UNIXSD parsers.
   All tests in this namespace are data-driven, for the purpose of building a library of inputs and expected outputs."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [cayenne.formats.unixsd :as unixsd]
            [cayenne.xml :as xml]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn])
  (:import [java.io PushbackReader]))

(def base "dev-resources/parser-regression")


(def types-tags
  "Mapping of input types and the top level tag that we look for.
   Pairs of directory-name, tag name"
  [["oai-pmh-unixref" "record"]
   ["queryresult-unixref-unixsd" "query_result"]])

(defn xml->itemtree-filename
  "Produce Item Tree EDN filename from XML (variety of schemas) file object."
  [file]
  (io/file (.getParent file) (.replaceAll (.getName file) "\\.xml$" ".itemtree.edn")))

(defn input-result-files
  "Given a directory of XML files, return a sequence of [xml file, edn output file]."
  [dir]
  (keep
   (fn [xml-file]
     (let [edn-file (xml->itemtree-filename xml-file)]
       (when (-> xml-file .getName (.endsWith ".xml"))
         [xml-file edn-file])))
   (file-seq dir)))


(defn missing-result-files
  "Given a directory of input XML files, return a sequence of [xml file, output files] when the output file doesn't exist."
  [dir]
  (->> dir
       input-result-files
       (remove #(-> % second .exists))))

(deftest ^:unit  all-inputs-should-have-outputs
  ; Make sure no data is left behind to become irrelevant.
  (testing "Every input XML file should a corresponding EDN output file."
           (doseq [[dir _] types-tags]
             (is (empty? (missing-result-files (io/file base dir)))
                 (str "Found orphaned XML files without Item Tree files in " dir ". Consider running generate-result-files.")))))

(defn generate-result-files
  "Manual function used when writing tests.
   Scan inputs and generate result EDN based on current code. These are then checked in and
   used for future regression tests.
   You should review the resulting files for correctness!"
  ([]
   (doseq [[dir tag] types-tags]
     (generate-result-files (io/file base dir) tag))
   (println "IMPORTANT! Manually verify that output files are correct!"))

  ([dir tag-name]
   (let [missing-files (missing-result-files dir)]
     (println "Found" (count missing-files) "missing files in" dir "to generate.")
     (doseq [[xml-file edn-file] missing-files]
             (println (.getName xml-file))
       (with-open [rdr (io/reader xml-file)]
         (let [elements (xml/get-elements rdr tag-name)
               parsed (map unixsd/unixsd-record-parser elements)]
           (when parsed
             ; Only create the file if there was an output.
             ; This will avoid creating empty files which will cause errors later.
             ; Better to have the absence of the file caught by all-inputs-should-have-outputs.
             (with-open [wrtr (io/writer edn-file)]
               (pprint parsed wrtr)))))))))

(deftest ^:unit  inputs-should-parse-expected
  (testing "Every XML file should parse to a known result"
           (doseq [[dir-name tag-name] types-tags]
             (println "Check dir" dir-name)
             (let [dir (io/file base dir-name)]
               (doseq [[xml-file edn-file] (input-result-files dir)]
                 (println "Check" (.getName xml-file))
                 (is (.exists edn-file)
                     "Result file should exist.")
                 (when (.exists edn-file)
                   (with-open [xml-reader (io/reader xml-file)
                               edn-reader (-> edn-file io/reader PushbackReader.)]
                     (let [elements (xml/get-elements xml-reader tag-name)
                           parsed (map unixsd/unixsd-record-parser elements)]
                       (is (= parsed (edn/read edn-reader)))))))))))
