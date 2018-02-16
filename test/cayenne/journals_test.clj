(ns cayenne.journals-test
  (:require [cayenne.api.v1.feed :refer [start-feed-processing]]
            [cayenne.conf :refer [set-param! with-core]]
            [cayenne.tasks :refer [load-journals load-last-day-works]]
            [cayenne.tasks.solr :refer [start-insert-list-processing]]
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

  (testing "journals endpoint returns expected result for offset"
    (doseq [offset [20 40]]
      (let [response (-> (http/get (str api-root "/v1/journals?offset=" offset) {:as :json})
                         :body
                         :message
                         (update-in [:items] (partial map #(dissoc % :last-status-check-time))))
            expected-response (read-string (slurp (str "test/resources/titles-offset-" offset ".edn")))]
        (is (= expected-response response)))))

  (testing "journals endpoint returns expected result for ISSN"
    (doseq [issn ["2542-1298" "2303-5595"]]
      (let [response (-> (http/get (str api-root "/v1/journals/" issn) {:as :json})
                         :body
                         :message
                         (dissoc :last-status-check-time))
            expected-response (read-string (slurp (str "test/resources/titles/" issn ".edn")))]
        (is (= expected-response response)))))
  
  (comment (testing "journals endpoint returns expected result for ISSN works"
    (doseq [issn ["2542-1298" "2303-5595"]]
      (let [response (-> (http/get (str api-root "/v1/journals/" issn "/works") {:as :json})
                         :body
                         :message
                         (dissoc :last-status-check-time))
            expected-response (read-string (slurp (str "test/resources/titles/" issn "-works.edn")))]
        (is (= expected-response response)))))))

(use-fixtures 
  :once 
  (api-with 
    (fn []
      (with-core :default 
        (comment (set-param! [:dir :data] "test/resources/works/"))
        (set-param! [:location :cr-titles-csv] "test/resources/titles.csv")) 
      (load-journals)
      (comment (start-feed-processing)))))
