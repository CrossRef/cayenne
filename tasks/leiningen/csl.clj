(ns leiningen.csl
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defn generate-locales-manifest []
  (->> (io/file "csl/locales")
       (.list)
       (filter #(.startsWith % "locales-"))
       (map #(-> %
                 (string/replace-first #"locales-" "")
                 (string/replace-first #"\.xml" "")))))

(defn generate-styles-manifest []
  (->> (io/file "csl/styles")
       (.list)
       (filter #(.endsWith % ".csl"))
       (map #(string/replace-first % #"\.csl" ""))))

(defn csl [project & args]
  (->> (generate-locales-manifest)
       pr-str
       (spit "resources/locales.edn"))
  (->> (generate-styles-manifest)
       pr-str
       (spit "resources/styles.edn")))
