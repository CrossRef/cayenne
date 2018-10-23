(ns cayenne.members-test
  (:require [cayenne.api-fixture :refer [api-get api-with-works]]
            [clojure.java.io :refer [resource]]
            [clojure.pprint :refer [pprint]]
            [clojure.test :refer [use-fixtures deftest testing is]]))

(deftest querying-members
  (testing "members endpoint returns expected result"
    (let [response (api-get "/v1/members")
          expected-response (read-string (slurp (resource "members.edn")))]
      (is (= expected-response response))))

  (testing "members endpoint returns expected result for member works"
    (doseq [member-id ["78"]]
      (let [response (api-get (str "/v1/members/" member-id "/works?rows=200"))
            expected-response (read-string (slurp (resource (str "members/" member-id "-works.edn"))))]
        (is (= expected-response response) (str "actual result: "  (with-out-str (pprint response))))))))

(use-fixtures 
  :once 
  api-with-works)
