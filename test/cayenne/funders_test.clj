(ns cayenne.funders-test
  (:require [cayenne.api-fixture :refer [api-root api-get api-with]]
            [clojure.java.io :refer [resource]]
            [clojure.test :refer [use-fixtures deftest testing is]]))

(deftest querying-funders
  (testing "funders endpoint returns expected result"
    (let [response (api-get "/v1/funders")
          expected-response (read-string (slurp (resource "funders.edn")))]
      (is (= expected-response response))))

  (testing "funders endpoint returns expected result for offset"
    (doseq [offset [20 40]]
      (let [response (api-get (str "/v1/funders?offset=" offset) :sorter :id)
            expected-response (read-string (slurp (resource (str "funders-offset-" offset ".edn"))))]
        (is (= expected-response response)))))

  (testing "funders endpoint returns expected result for funder"
    (doseq [funder ["100000001" "100006151" "501100000315" "501100000314"]]
      (let [response (api-get (str "/v1/funders/" funder))
            expected-response (read-string (slurp (resource (str "funders/" funder ".edn"))))]
        (is (= expected-response response))))))

(use-fixtures
  :once
  (api-with
    #(do (user/load-test-funders)
         ;; todo wait until indexing finished
         (Thread/sleep 5000))))
