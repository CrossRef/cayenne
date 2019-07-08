(ns cayenne.funders-test
  (:require [cayenne.api-fixture :refer [api-root api-get api-with]]
            [clojure.java.io :refer [resource]]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [cayenne.tasks.funder :as funder]
            [cayenne.conf :as conf]
            [cayenne.rdf :as rdf]))

(comment
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
        (is (= expected-response response)))))))

;; consolidate resource so that the same resource can be tested across multiple tests?
(deftest ^:unit check-res->id
  (testing "unit testing res->id"
  (let [model (-> (java.net.URL. (conf/get-param [:location :cr-funder-registry])) rdf/document->model)
        funders (->> model funder/find-funders (partition-all 5))
        funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000130"})) first)
        response (funder/res->id funder-resource)
        expected-response "100000130"]
    (is (= response expected-response)))))

(deftest ^:unit check-res->doi
  (testing "unit testing res->doi"
    (let [model (-> (java.net.URL. (conf/get-param [:location :cr-funder-registry])) rdf/document->model)
          funders (->> model funder/find-funders (partition-all 5))
          funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000130"})) first)
          response (funder/res->doi funder-resource)
          expected-response "10.13039/100000130"]
      (is (= response expected-response)))))

(deftest ^:unit check-ancestor
  (testing "unit testing check ancestor"
  (let [model (-> (java.net.URL. (conf/get-param [:location :cr-funder-registry])) rdf/document->model)
        funders (->> model funder/find-funders (partition-all 5))
        funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000130"})) first)
        ancestors (funder/resource-ancestors model funder-resource)
        response (set (->> ancestors (map funder/res->id) distinct sort))
        expected-response #{"100000016" "100000030"}]
        (is (= response expected-response)))))



(use-fixtures
  :once
  (api-with
    #(do (user/load-test-funders)
         (user/index-feed))))
