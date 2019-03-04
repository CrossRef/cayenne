(ns cayenne.ctn-test
  (:require [clojure.test :refer :all]
            [cayenne.ids.ctn :refer :all]))

(deftest ^:unit normalize-ctn-test
  (testing "normalize-ctn converts to lower case"
    (is (= (normalize-ctn "ISRCTN12345") "isrctn12345"))
    (is (= (normalize-ctn "isrctn12345") "isrctn12345"))))

(deftest ^:unit ctn-proxy-test
  (testing "ctn-proxy removes non-significant characters."
    (is (= (ctn-proxy "ISRCTN12345") (ctn-proxy "ISRCTN12345")))
    (is (= (ctn-proxy "ISRCTN12345") (ctn-proxy "isrctn12345")))
    (is (= (ctn-proxy "ISRCTN-12345") (ctn-proxy "ISRCTN12345")))
    (is (= (ctn-proxy "ISRCTN___12345") (ctn-proxy "ISRCTN12345")))))
    



