(ns cayenne.funders-test
  (:require [cayenne.api-fixture :refer [api-root api-with]]
            [cayenne.tasks.funder :refer [load-funders-csv]]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [clj-http.client :as http]))

(deftest querying-funders
  (testing "funders endpoint returns expected result"
    (let [response (-> (http/get (str api-root "/v1/funders") {:as :json})
                       :body
                       :message)
          expected-response (read-string (slurp "test/resources/funders.edn"))]
      (is (= expected-response response))))
  
  (testing "funders endpoint returns expected result for funder"
    (doseq [funder ["501100000314" "501100000315"]]
      (let [response (-> (http/get (str api-root "/v1/funders/" funder) {:as :json})
                         :body
                         :message)
            expected-response (read-string (slurp (str "test/resources/funders/" funder ".edn")))]
        (is (= expected-response response))))))

(use-fixtures 
  :once 
  (api-with load-funders-csv))
