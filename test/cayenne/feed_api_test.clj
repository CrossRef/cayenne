(ns cayenne.feed-api-test
  (:require [cayenne.api-fixture :refer [api-root feed-ready-api]]
            [cayenne.conf :refer [get-param]]
            [clj-http.client :as http]
            [clojure.data.codec.base64 :refer [encode]]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [nio2.io :refer [path]]
            [nio2.dir-seq :refer [dir-seq-glob]]))

(deftest pushing-files
  (testing "file is deposited exactly as pushed"
    (doseq [file ["crossref-unixsd-f0e08fdd-459c-4b59-96da-ede1a1483f81.body"]]
      (let [feed-dir (get-param [:dir :data])
            feed-file (str feed-dir "/corpus/" file)
            feed-in-dir (str feed-dir "/feed-in")
            token (String. (encode (.getBytes "crossref:development-token")))
            response (http/post (str api-root "/v1/feeds/crossref") 
                                {:body (slurp feed-file)
                                 :headers {"authorization" (str "Basic " token)
                                           "content-type" "application/vnd.crossref.unixsd+xml"}})
            expected-response (slurp feed-file)]
        (is (= expected-response (slurp (str (first (dir-seq-glob (path feed-in-dir) "*.body"))))))))))

(use-fixtures 
  :once 
  feed-ready-api)
