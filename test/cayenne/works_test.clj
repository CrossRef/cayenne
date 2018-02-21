(ns cayenne.works-test
  (:require [cayenne.api-fixture :refer [api-root api-with-works]]
            [clojure.data.json :refer [write-str]]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [clojure.java.io :refer [resource]]
            [clj-http.client :as http]))

(deftest querying-works
  (testing "works endpoint returns expected result for DOI"
    (doseq [doi ["10.1016/j.psyneuen.2016.10.018"]]
      (let [response (-> (http/get (str api-root "/v1/works/" doi) {:as :json})
                         :body
                         :message
                         (dissoc :indexed))
            expected-response (read-string (slurp (resource (str "works/" doi ".edn"))))]
        (is (= expected-response response)))))
  
  (testing "works endpoint returns expected result for DOI agency"
    (with-redefs 
      [cayenne.data.work/get-agency 
       (fn [d]
         (case d
           "10.1016/j.psyneuen.2016.10.018" {:body (write-str [{:RA "Crossref"}])}
           "10.5167/UZH-30455" {:body (write-str [{:RA "DataCite"}])}
           {:body (write-str [{:RA "Unknown Agency"}])}))]
      (doseq [doi ["10.1016/j.psyneuen.2016.10.018" "10.5167/UZH-30455"]]
        (let [response (-> (http/get (str api-root "/v1/works/" doi "/agency") {:as :json})
                           :body
                           :message)
              expected-response (read-string (slurp (resource (str "works/" doi "-agency.edn"))))]
          (is (= expected-response response)))))))

(use-fixtures 
  :once 
  api-with-works)
