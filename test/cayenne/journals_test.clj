(ns cayenne.journals-test
  (:require [cayenne.api-fixture :refer [api-root api-with-works]]
            [clojure.java.io :refer [resource]]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [clj-http.client :as http]))

(deftest querying-journals
  (testing "journals endpoint returns expected result"
    (let [response (-> (http/get (str api-root "/v1/journals") {:as :json})
                       :body
                       :message
                       (update-in [:items] (partial map #(dissoc % :last-status-check-time))))
          expected-response (read-string (slurp (resource "titles.edn")))]
      (is (= expected-response response))))

  (testing "journals endpoint returns expected result for offset"
    (doseq [offset [20 40]]
      (let [response (-> (http/get (str api-root "/v1/journals?offset=" offset) {:as :json})
                         :body
                         :message
                         (update-in [:items] (partial map #(dissoc % :last-status-check-time))))
            expected-response (read-string (slurp (resource (str "titles-offset-" offset ".edn"))))]
        (is (= expected-response response)))))

  (testing "journals endpoint returns expected result for ISSN"
    (doseq [issn ["0306-4530" "2542-1298" "2303-5595"]]
      (let [response (-> (http/get (str api-root "/v1/journals/" issn) {:as :json})
                         :body
                         :message
                         (dissoc :last-status-check-time))
            expected-response (read-string (slurp (resource (str "titles/" issn ".edn"))))]
        (is (= expected-response response)))))
  
  (testing "journals endpoint returns expected result for ISSN works"
    (doseq [issn ["0306-4530"]]
      (let [response (-> (http/get (str api-root "/v1/journals/" issn "/works?rows=76") {:as :json})
                         :body
                         :message
                         (update-in [:items] (partial map #(dissoc % :indexed)))
                         (update-in [:items] (partial sort-by :DOI)))
            expected-response (read-string (slurp (resource (str "titles/" issn "-works.edn"))))]
        (is (= expected-response response))))))

(use-fixtures 
  :once 
  api-with-works)
