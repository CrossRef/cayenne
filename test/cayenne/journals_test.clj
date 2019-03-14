(ns cayenne.journals-test
  (:require [cayenne.api-fixture :refer [api-root api-get api-with-works no-scores]]
            [clojure.java.io :refer [resource]]
            [clojure.test :refer [use-fixtures deftest testing is]]))

(def present-issns
  "These are known to be present in the test fixture titles.csv file"
  #{"2542-1298" "2303-5595"})

(deftest ^:integration all-journals
  (testing "Journals endpoint includes all known journals."
    (let [response (api-get "/v1/journals")]
      (is (every? (->> response :items (mapcat :ISSN) set) present-issns)
          "Every known ISSN should be present in the response."))))

(deftest ^:integration journal-by-issn
  (testing "Journal endpoint returns journals with the given ISSN."
    (doseq [issn present-issns]
      (let [response (api-get (str "/v1/journals/" issn))]

        (is (-> response :ISSN set (contains? issn))
            "ISSN should be returned")
        (is (contains? (->> response :issn-type (map :value) set)
                       issn)
            "ISSN should be present in one of the issn types")))))

(deftest ^:integration works-by-journal
  (testing "All works retrieved by ISSN have the correct ISSN."
           ; NB Weirdly, this one isn't present in the title file.
           (let [issn "0306-4530"
                 response (api-get (str "/v1/journals/" issn "/works"))]
        (is (-> response :items not-empty) "Some results returned.")
        (is (->> response :items (mapcat :ISSN) set (every? #{issn}))
            "Every item should have the given ISSN"))))

(use-fixtures
  :once
  api-with-works)
