(ns cayenne.funders-test
  (:require [cayenne.api-fixture :refer [api-root api-get api-with]]
            [clojure.java.io :refer [resource]]
            [cayenne.rdf :as rdf]
            [cayenne.conf :as conf]
            [cayenne.tasks.funder :as funder]
            [clojure.test :refer [use-fixtures deftest testing is]]))

(deftest ^:integration querying-funders
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
        (is (= expected-response response)))))

  (testing "funders/works endpoint returns expected result for funder"
    (doseq [funder ["100000002" "100009429" "501100001602" ]]
      (let [response (api-get (str "/v1/funders/" funder "/works?rows=1000"))
            expected-response (read-string (slurp (resource (str "funders/" funder "-works.edn"))))]
        (is (= expected-response response))))))

(deftest ^:unit check-index-command-output
  (testing "index-command output which returns es-id and function generate-es-object yields same output except for the inclusion of elastic search index id"
  (let [model (-> (java.net.URL. (conf/get-param [:location :cr-funder-registry])) rdf/document->model)
        funders (first (->> model funder/find-funders (partition-all 5)))
        with-es-id-output (->> funders (map (partial funder/index-command model)) flatten)
        remove-id (filter #(not(contains? % :index)) with-es-id-output)
        without-es-id-output (->> funders (map (partial funder/generate-es-object model)) flatten)]
        (is (= remove-id without-es-id-output)))))

(use-fixtures
  :once
  (api-with
    #(do (user/load-test-funders)
         (user/index-feed))))
