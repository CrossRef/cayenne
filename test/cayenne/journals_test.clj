(ns cayenne.journals-test
  (:require [cayenne.api.v1.feed :refer [start-feed-processing]]
            [cayenne.conf :refer [set-param! with-core]]
            [cayenne.tasks :refer [load-journals load-last-day-works]]
            [cayenne.tasks.coverage :refer [check-journals]]
            [cayenne.tasks.solr :refer [start-insert-list-processing]]
            [cayenne.api-fixture :refer [api-root api-with solr-doc-count]]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [clj-http.client :as http]
            [me.raynes.fs :refer [copy-dir delete-dir]]
            [nio2.io :refer [path]]
            [nio2.dir-seq :refer [dir-seq-glob]]))

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
    (doseq [issn ["0306-4530" "2542-1298" "2303-5595"]]
      (let [response (-> (http/get (str api-root "/v1/journals/" issn) {:as :json})
                         :body
                         :message
                         (dissoc :last-status-check-time))
            expected-response (read-string (slurp (str "test/resources/titles/" issn ".edn")))]
        (is (= expected-response response)))))
  
  (testing "journals endpoint returns expected result for ISSN works"
    (doseq [issn ["0306-4530"]]
      (let [response (-> (http/get (str api-root "/v1/journals/" issn "/works?rows=76") {:as :json})
                         :body
                         :message
                         (update-in [:items] (partial map #(dissoc % :indexed)))
                         (update-in [:items] (partial sort-by :DOI)))
            expected-response (read-string (slurp (str "test/resources/titles/" issn "-works.edn")))]
        (is (= expected-response response))))))

(use-fixtures 
  :once 
  (api-with 
    (fn []
      (let [feed-dir "test/resources/feeds"
            feed-source-dir (str feed-dir "/source")
            feed-in-dir (str feed-dir "/feed-in")
            feed-processed-dir (str feed-dir "/feed-processed")
            feed-file-count (count (dir-seq-glob (path feed-source-dir) "*.body"))]
        (delete-dir feed-processed-dir)
        (delete-dir feed-in-dir)
        (copy-dir feed-source-dir feed-in-dir)
        (with-core :default 
          (set-param! [:dir :data] feed-dir)
          (set-param! [:dir :test-data] feed-dir)
          (set-param! [:location :cr-titles-csv] "test/resources/titles.csv")
          (set-param! [:service :solr :insert-list-max-size] 0))
        (load-journals)
        (start-insert-list-processing)
        (start-feed-processing)
        (while (not= (solr-doc-count) feed-file-count)
          (println "Waiting for solr to finish indexing....")
          (Thread/sleep 1000))
        (check-journals "journals")))))
      
