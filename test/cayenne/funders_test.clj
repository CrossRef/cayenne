(ns cayenne.funders-test
  (:require [cayenne.conf :refer [with-core set-param!]]
            [cayenne.api-fixture :refer [api-root api-with]]
            [cayenne.tasks :refer [load-funders]]
            [clojure.java.io :refer [resource]]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [clj-http.client :as http]))

(deftest querying-funders
  (testing "funders endpoint returns expected result"
    (let [response (-> (http/get (str api-root "/v1/funders") {:as :json})
                       :body
                       :message)
          expected-response (read-string (slurp (resource "funders.edn")))]
      (is (= expected-response response))))

  (testing "funders endpoint returns expected result for offset"
    (doseq [offset [20 40]]
      (let [response (-> (http/get (str api-root "/v1/funders?offset=" offset) {:as :json})
                         :body
                         :message)
            expected-response (read-string (slurp (resource (str "funders-offset-" offset ".edn"))))]
        (is (= expected-response response)))))
  
  (testing "funders endpoint returns expected result for funder"
    (doseq [funder ["100000001" "100006151" "501100000315" "501100000314"]]
      (let [response (-> (http/get (str api-root "/v1/funders/" funder) {:as :json})
                         :body
                         :message 
                         (dissoc :work-count)
                         (dissoc :descendant-work-count))
            expected-response (-> (read-string (slurp (resource (str "funders/" funder ".edn"))))
                                  (dissoc :work-count)
                                  (dissoc :descendant-work-count))]
        (is (= expected-response response))))))

(use-fixtures 
  :once 
  (api-with 
    (fn [] 
      (with-core :default
        (->> (.getPath (resource "registry.rdf"))
             (str "file://")
             (set-param! [:location :cr-funder-registry])))
      (load-funders))))
