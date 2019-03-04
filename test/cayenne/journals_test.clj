(ns cayenne.journals-test
  (:require [cayenne.api-fixture :refer [api-root api-get api-with-works no-scores]]
            [clojure.java.io :refer [resource]]
            [clojure.test :refer [use-fixtures deftest testing is]]))

(deftest ^:integration querying-journals
  (testing "journals endpoint returns expected result"
    (let [response (api-get "/v1/journals")
          expected-response (read-string (slurp (resource "titles.edn")))]
      (is (= expected-response response)))))

(deftest ^:integration querying-journals
  (testing "journals endpoint returns expected result for offset"
    (doseq [offset [20 40]]
      (let [response (api-get (str "/v1/journals?offset=" offset))
            expected-response (read-string (slurp (resource (str "titles-offset-" offset ".edn"))))]
        (is (= expected-response response))))))

(deftest ^:integration querying-journals
  (testing "journals endpoint returns expected result for ISSN"
    (doseq [issn ["0306-4530" "2542-1298" "2303-5595"]]
      (let [response (api-get (str "/v1/journals/" issn))
            expected-response (read-string (slurp (resource (str "titles/" issn ".edn"))))]
        (is (= expected-response response))))))

(deftest ^:integration querying-journals
  (testing "journals endpoint returns expected result for ISSN works"
    (doseq [issn ["0306-4530"]]
      (let [response (-> (api-get (str "/v1/journals/" issn "/works?rows=76"))
                         no-scores)
            expected-response (read-string (slurp (resource (str "titles/" issn "-works.edn"))))]
        (is (= expected-response response))))))

(use-fixtures
  :once
  api-with-works)
