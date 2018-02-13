(ns cayenne.journals-test
  (:require [cayenne.conf :refer [set-param!]]
            [cayenne.tasks :refer [load-journals load-last-day-works]]
            [cayenne.api-fixture :refer [api-root api-with]]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [clj-http.client :as http]))

(deftest querying-journals
  (testing "journals endpoint returns expected result"
    (let [response (-> (http/get (str api-root "/v1/journals") {:as :json})
                       :body
                       :message
                       (update-in [:items] (partial map #(dissoc % :last-status-check-time))))
          expected-response (read-string (slurp "test/resources/titles.edn"))]
      (is (= expected-response response))))
  
  (testing "journals endpoint returns expected result for ISSN"
    (doseq [issn ["2542-1298" "2303-5595"]]
      (let [response (-> (http/get (str api-root "/v1/journals/" issn) {:as :json})
                         :body
                         :message
                         (dissoc :last-status-check-time))
            expected-response (read-string (slurp (str "test/resources/titles/" issn ".edn")))]
        (is (= expected-response response))))))

(use-fixtures 
  :once 
  (api-with (fn []
              (comment (load-last-day-works))
              (set-param! [:location :cr-titles-csv] "test/resources/titles.csv") 
              (load-journals))))
