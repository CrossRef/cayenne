(ns cayenne.data.csl
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [cayenne.api.v1.response :as r]))

(def locale-cache
  (->> "locales-en-GB.xml"
       io/resource
       io/file
       (.getParentFile)
       (.list)
       (filter #(.startsWith % "locales-"))
       (map #(-> % 
                 (string/replace-first #"locales-" "")
                 (string/replace-first #"\.xml" "")))))

(def style-cache
  (->> "apa.csl"
       io/resource
       io/file
       (.getParentFile)
       (.list)
       (filter #(.endsWith % ".csl"))
       (map #(string/replace-first % #"\.csl" ""))))

(defn fetch-all-styles []
  (-> (r/api-response :style-list)
      (r/with-result-items (count style-cache) style-cache)))

(defn fetch-all-locales []
  (-> (r/api-response :locale-list)
      (r/with-result-items (count locale-cache) locale-cache)))

