(ns cayenne.funders-test
  (:require [cayenne.api-fixture :refer [api-root api-get api-with]]
            [clojure.java.io :refer [resource]]
            [clojure.java.io :as io]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [cayenne.tasks.funder :as funder]
            [cayenne.conf :as conf]
            [cayenne.rdf :as rdf]))


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

;; consolidate resource so that the same resource can be tested across multiple tests?
(deftest ^:unit check-res->id
  (testing "unit testing res->id"
  (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
        funders (->> model funder/find-funders (partition-all 5))
        funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000030"})) first)
        response (funder/res->id funder-resource)
        expected-response "100000030"]
    (is (= response expected-response)))))

(deftest ^:unit check-res->doi
  (testing "unit testing res->doi"
    (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
          funders (->> model funder/find-funders (partition-all 5)))
          funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/1000000030"})) first)
          response (funder/res->doi funder-resource)
          expected-response "10.13039/100000030"]
      (is (= response expected-response)))))

(deftest ^:unit check-ancestor
  (testing "unit testing check ancestor"
  (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
        funders (->> model funder/find-funders (partition-all 5))
        funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000130"})) first)
        ancestors (funder/resource-ancestors model funder-resource)
        response (set (->> ancestors (map funder/res->id) distinct sort))
        expected-response #{"100000016" "100000030"}]
        (is (= response expected-response)))))

(deftest ^:unit check-parent
  (testing "unit testing check parent"
  (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
        funders (->> model funder/find-funders (partition-all 5))
        funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000030"})) first)
        response (-> model (funder/broader funder-resource) first funder/res->doi)
        expected-response "10.13039/100000016"]
  (is (= response expected-response)))))

(deftest ^:unit check-descendant
  (testing "unit testing check descendent"
  (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
        funders (->> model funder/find-funders (partition-all 5))
        funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000030"})) first)
        descendants (funder/resource-descendants model funder-resource)
        response (set (->> descendants (map funder/res->id) distinct sort))
        expected-response #{"100000125" "100000130" "100005195" "100005217" "100005218" "100005220" "100005222" "100005224" "100005258" "100005260" "100005262" "100005265" "100006087" "100006088" "100006089" "100006090"}]
      (is (= response expected-response)))))

 (deftest ^:unit check-children
    (testing "unit testing check children"
    (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
          funders (->> model funder/find-funders (partition-all 5))
          funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000030"})) first)
          response (distinct (map funder/res->id (funder/narrower model funder-resource)))
          expected-response #{"100000125" "100005195" "100005222" "100006090" "100000130" "100006087" "100005217" "100006089" "100005260" "100005258" "100006088" "100005218" "100005265" "100005262" "100005224" "100005220"}]
    (is (= response expected-response)))))

  (deftest ^:unit check-country
    (testing "unit testing check country"
    (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
        funders (->> model funder/find-funders (partition-all 5))
        funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000030"})) first)
        response (funder/get-country-literal-name model funder-resource)
        expected-response "United States"]
    (is (= response expected-response)))))

  (deftest ^:unit check-primary-name
    (testing "unit testing check primary name"
    (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
          funders (->> model funder/find-funders (partition-all 5))
          funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000030"})) first)
          response (-> model (funder/get-labels funder-resource "prefLabel") first)
          expected-response "Centers for Disease Control and Prevention"]
      (is (= response expected-response)))))

  (deftest ^:unit check-alternate-name
    (testing "unit testing check alternate name"
    (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
          funders (->> model funder/find-funders (partition-all 5))
          funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100000030"})) first)
          response (-> model (funder/get-labels funder-resource "altLabel") first)
          expected-response "CDC"]
      (is (= response expected-response)))))

  (deftest ^:unit check-affiliation
    (testing "unit testing check affiliation"
    (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
          funders (->> model funder/find-funders (partition-all 5))
          funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100009658"})) first)
          response (first (distinct (map funder/res->id (funder/affiliated model funder-resource))))
          expected-response "100005924"]
      (is (= response expected-response)))))

  (deftest ^:unit check-replaces
    (testing "unit testing check replaces"
    (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
          funders (->> model funder/find-funders (partition-all 5))
          funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100009794"})) first)
          response (first (distinct (map funder/res->id (funder/replaces model funder-resource))))
          expected-response "501100007913"]
      (is (= response expected-response)))))

  (deftest ^:unit check-replaced-by
    (testing "unit testing check replaced by"
    (let [model (-> (io/file "dev-resources/registry.rdf") rdf/document->model)
          funders (->> model funder/find-funders (partition-all 5))
          funder-resource (->> funders (apply concat) (filter #(-> % rdf/->uri #{"http://dx.doi.org/10.13039/100010260"})) first)
          response (first (distinct (map funder/res->id (funder/replaced-by model funder-resource))))
          expected-response "501100001707"]
        (is (= response expected-response)))))


(use-fixtures
  :once
  (api-with
    #(do (user/load-test-funders)
         (user/index-feed))))
