(ns cayenne.works-test
  (:require [cayenne.api-fixture :refer [api-root api-get api-with-works]]
            [clj-http.client :as http]
            [clojure.data.json :refer [write-str]]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [clojure.java.io :refer [resource]]))

(deftest querying-works
  (testing "works endpoint returns expected result for DOI"
    (doseq [doi ["10.1016/j.psyneuen.2016.10.018" 
                 "10.7287/peerj.2196v0.1/reviews/2" 
                 "10.7287/peerj.1078v0.2/reviews/1"
                 "10.7717/peerj.1698"
                 "10.1084/jem.20151673"]]
      (let [response (api-get (str "/v1/works/" doi))
            expected-response (read-string (slurp (resource (str "works/" doi ".edn"))))]
        (is (= expected-response response) (str "Unexpected response for DOI " doi)))))

  (testing "works endpoint returns expected result for query"
    (doseq [q-filter ["query.title=Peer" "query.title=Socioeconomic"]]
      (let [response (api-get (str "/v1/works?" q-filter))
            expected-response (read-string (slurp (resource (str "works/" q-filter ".edn"))))]
        (is (= expected-response response) (str "unexpected result for query " q-filter)))))

  (testing "works endpoint returns expected result for select"
    (doseq [select ["volume" "title" "DOI" "member"]]
      (let [response (api-get (str "/v1/works?select=" select "&sort=created"))
            expected-response (read-string (slurp (resource (str "works/?select=" select ".edn"))))]
        (is (= expected-response response) (str "unexpected result for select " select)))))

  (testing "works endpoint returns expected result for filter"
    (doseq [q-filter ["type:peer-review" "from-created-date:2018" 
                      "from-deposit-date:2018" "from-pub-date:2018"
                      "member:78"]]
      (let [response (api-get (str "/v1/works?filter=" q-filter))
            expected-response (read-string (slurp (resource (str "works/?filter=" q-filter ".edn"))))]
        (is (= expected-response response) (str "unexpected result for filter " q-filter)))))

  (testing "works related endpoints agree on work counts"
    (let [work-count (:total-results (api-get (str "/v1/works?filter=member:78")))
          member-work-count (:total-results (api-get (str "/v1/members/78/works")))
          member-doi-count (-> (api-get (str "/v1/members/78")) :counts :total-dois)]
      (is (= work-count member-work-count member-doi-count))))

  (testing "works endpoint returns expected result for transform"
    (doseq [doi ["10.1016/j.psyneuen.2016.10.018" "10.7287/peerj.2196v0.1/reviews/2"]]
      (doseq [content-type (->> cayenne.api.v1.types/work-transform
                                (remove #{"application/x-bibtex" 
                                          "application/json" 
                                          "application/citeproc+json" 
                                          "application/vnd.citationstyles.csl+json"}))]
        (with-redefs 
          [org.httpkit.client/get 
           (fn [_ _]
             ;; trasforming to unixref and unixsd xml results in a call to an upstream http
             ;; service, here we mock it out to slurp from a file instead
             (let [file (if (clojure.string/ends-with? content-type "unixref+xml")
                            "application/vnd.crossref.unixref+xml"
                            "application/vnd.crossref.unixsd+xml")]
                 (atom {:body (slurp (resource (str "works/" doi "/" file)))})))]
          (let [response (->> {:accept content-type} 
                              (http/get (str api-root "/v1/works/" doi "/transform"))
                              :body)
                expected-response (slurp (resource (str "works/" doi "/transform/" content-type)))]
            (is (= expected-response response) (str "unexpected result for transform of " doi " to " content-type)))))))

  (testing "works endpoint returns expected result for DOI agency"
    (with-redefs 
      [cayenne.data.work/get-agency 
       (fn [d]
         (case d
           "10.1016/j.psyneuen.2016.10.018" {:body (write-str [{:RA "Crossref"}])}
           "10.5167/UZH-30455" {:body (write-str [{:RA "DataCite"}])}
           {:body (write-str [{:RA "Unknown Agency"}])}))]
      (doseq [doi ["10.1016/j.psyneuen.2016.10.018" "10.5167/UZH-30455"]]
        (let [response (api-get (str "/v1/works/" doi "/agency"))
              expected-response (read-string (slurp (resource (str "works/" doi "-agency.edn"))))]
          (is (= expected-response response)))))))

(use-fixtures 
  :once 
  api-with-works)
