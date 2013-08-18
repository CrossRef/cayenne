(ns cayenne.data.core
  (:require [clojure.core.incubator :refer [dissoc-in]]
            [cayenne.conf :as conf]))

(defn clean-core
  "Produce a cleaned up core ready for JSON display."
  [core]
  (-> (get-in core [:parameters])
      (dissoc-in [:service :api :var])))

(defn exists? [name]
  (some #{(keyword name)} (keys @conf/cores)))

(defn fetch [name]
  (-> (get @conf/cores (keyword name))
      (clean-core)))

(defn fetch-all []
  (keys @conf/cores))

