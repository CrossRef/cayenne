(ns cayenne.data.core
  (:require [clojure.core.incubator :refer [dissoc-in]]
            [cayenne.api.v1.response :as r]
            [cayenne.conf :as conf]))

(defn exists? [name]
  (some #{(keyword name)} (keys @conf/cores)))

(defn fetch [name]
  (let [core (get @conf/cores (keyword name))]
    (r/api-response :core :content core)))

(defn fetch-all []
  (let [cores (keys @conf/cores)]
    (-> (r/api-response :core-list)
        (r/with-result-items (count cores) cores))))


