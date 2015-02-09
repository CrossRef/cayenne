(ns cayenne.data.csl
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [cayenne.conf :as conf]
            [cayenne.api.v1.response :as r]))

(def locale-cache
  (-> (conf/get-resource :locales)
      slurp
      read-string))

(def style-cache
  (-> (conf/get-resource :styles)
      slurp
      read-string))

(defn fetch-all-styles []
  (-> (r/api-response :style-list)
      (r/with-result-items (count style-cache) style-cache)))

(defn fetch-all-locales []
  (-> (r/api-response :locale-list)
      (r/with-result-items (count locale-cache) locale-cache)))

