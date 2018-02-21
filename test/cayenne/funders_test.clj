(ns cayenne.funders-test
  (:require [cayenne.conf :refer [with-core set-param!]]
            [cayenne.api-fixture :refer [api-root api-with]]
            [cayenne.tasks :refer [load-funders]]
            [cayenne.tasks.funder :refer [select-country-stmts]]
            [cayenne.rdf :as rdf]
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
      (with-redefs 
        [cayenne.tasks.funder/get-country-literal-name 
         (fn [model node] 
           (let [url (rdf/->uri (first (rdf/objects (select-country-stmts model node))))]
             (case url
               "http://sws.geonames.org/2921044/" "Germany"
               "http://sws.geonames.org/6252001/" "United States"
               "http://sws.geonames.org/2077456/" "Australia"
               "http://sws.geonames.org/337996/" "Ethiopia"
               "http://sws.geonames.org/1814991/" "China"
               "http://sws.geonames.org/2635167/" "United Kingdom"
               "http://sws.geonames.org/3144096/" "Norway"
               "http://sws.geonames.org/2661886/" "Sweden"
               "http://sws.geonames.org/1861060/" "Japan"
               url)))]
        (load-funders)))))
