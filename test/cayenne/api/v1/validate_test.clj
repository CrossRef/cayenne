(ns cayenne.api.v1.validate-test
  (:require [clojure.test :refer [deftest testing are]]
            [cayenne.api.v1.validate :refer [validate-pair-list-form]]))

(deftest validate-pair-list-form-test
  (testing "returns expected result when :also is nil"
    (are [pair context expected] (= (validate-pair-list-form context pair :also nil) expected)
         "a:1" {} {}
         "b:1" {:key "value"} {:key "value"}
         "b" {:key "value"} {:key "value"
                             :failures
                             [{:type :pair-list-form-invalid,
                               :value "b",
                               :message
                               "Pair list form specified as 'b' but should be of the form: key:val,...,keyN:valN"}]}))

  (testing "returns expected result when :also is non nil"
    (are [pair context also expected] (= (validate-pair-list-form context pair :also also) expected)
         "a:1" {} ["a:1"] {}
         "a" {:key "value"} ["a"] {:key "value"}
         "b:1" {:key "value"} ["b:1"] {:key "value"}
         "b:1" {:key "value"} ["a:1"] {:key "value"}
         "b" {:key "value"} [] {:key "value"
                                :failures
                                [{:type :pair-list-form-invalid,
                                  :value "b",
                                  :message
                                  "Pair list form specified as 'b' but should be of the form: key:val,...,keyN:valN"}]})))
