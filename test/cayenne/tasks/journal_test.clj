(ns cayenne.tasks.journal-test
  (:require
    [cayenne.tasks.journal :as journal]
    [clojure.java.io :as io]
    [cayenne.conf :as conf]
    [cayenne.api-fixture :as api-fixture]
    [clojure.test :refer [deftest is testing use-fixtures]]))

(deftest ^:unit column-ordering
  (testing "A csv file can be loaded regardless of column ordering."
           (with-open [rdr-1 (io/reader "dev-resources/cayenne/tasks/journal_test/10-titles.csv")
                       rdr-2 (io/reader "dev-resources/cayenne/tasks/journal_test/10-titles-out-of-order.csv")]
             (let [titles-1 (journal/fetch-titles rdr-1)
                   titles-2 (journal/fetch-titles rdr-2)]
               (is (= titles-1 titles-2)
                   "The same data with titles out of order is parsed to the same output")))))

(deftest ^:unit index-command-fields
  (let [test-row
          {:JournalID "296095"
           :pissn "1607419X"
           :eissn "24118524"
           :doi "10.5555/12345678"
           :JournalTitle "Journal of Inverse Reactance"
           :Publisher "Books Books Books"}]
    (testing "index-command should return tuple of elastic-command, elastic-body."
             (let [[command body] (journal/index-command test-row)]
               (is (= command {:index {:_id 296095}}) "The Elastic Search document ID is correct.")
               (is (= (:id body) 296095) "The id field in the document is the same.")))

    (testing "fields should be represented correctly"
               (is (= (-> test-row
                          journal/index-command
                          second
                          :issn
                          set)
                      #{{:value "1607-419X" :type "print"}
                        {:value "2411-8524" :type "electronic"}})
                   "ISSNs should be included along with type (order irrelevant)."))

               (is (= (-> test-row
                          journal/index-command
                          second
                          :doi)
                      "10.5555/12345678")
                   "DOI should be indexed when present.")

                 ; DOI in a different format.
                 (is (= (-> (assoc test-row :doi "https://doi.org/10.5555/12345678")
                             journal/index-command
                             second
                             :doi)
                         "10.5555/12345678")
                      "DOI should be normalised when present.")

                 (is (= (-> test-row
                            journal/index-command
                            second
                            :title)
                        "Journal of Inverse Reactance")
                     "Title should be indexed.")

                (is (= (-> test-row
                           journal/index-command
                           second
                           :publisher)
                       "Books Books Books")
                    "Publisher name should be indexed.")

           (with-redefs [cayenne.util/tokenize-name
                         ; Mock out the tokenizing function to expects the given title.
                         {"Journal of Inverse Reactance"
                          ["journal" "of" "inverse" "reactance"]}]

                         (is (= (-> test-row
                                    journal/index-command
                                    second
                                    :token)
                                ["journal" "of" "inverse" "reactance"])
                             "token should be the result of tokenizing the title field"))))


(deftest ^:integration all-titles-indexed
  (testing "When all titles are ingested there should be the same number in the index."

           ; Replace remote URL with a local test file resource.
           (conf/set-param! [:location :cr-titles-csv]
                            (io/resource "cayenne/tasks/journal_test/all-titles.csv"))

           (println "Indexing journals...")
           (journal/index-journals)

           (let [num-lines (with-open [r (-> "cayenne/tasks/journal_test/all-titles.csv"
                                             io/resource
                                             io/reader)]
                             (-> r line-seq count))]

             (= (-> "/v1/journals" api-fixture/api-get :total-results)
                ; Ignore header line.
                (dec num-lines)
                "Number of results should match number of entries in the input file."))))

(use-fixtures
 :once
 api-fixture/empty-api)