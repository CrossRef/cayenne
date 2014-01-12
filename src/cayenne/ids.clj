(ns cayenne.ids
  (:require [cayenne.conf :as conf]))

(defn get-id-uri [id-type id-value]
  (if-let [prefix (conf/get-param [:id (keyword id-type) :path])]
    (str prefix id-value)
    (str (conf/get-param [:id-generic :path]) (name id-type) "/" id-value)))

(defn get-data-uri [id-type id-value]
  (if-let [prefix (conf/get-param [:id (keyword id-type) :data-path])]
    (str prefix id-value)
    (str (conf/get-param [:id-generic :data-path]) (name id-type) "/" id-value)))

(defn to-supplementary-id-uri [id-value]
  (str (conf/get-param [:id :supplementary :path]) id-value))

(defn extract-supplementary-id [id-uri]
  (clojure.string/replace id-uri #"\Ahttp:\/\/id\.crossref\.org\/supp\/" ""))

;; todo generalize by looking at keys in get-param [:id]
(defn id-uri-type [id-uri]
  (cond (.startsWith id-uri (conf/get-param [:id :issn :path]))
        :issn
        (.startsWith id-uri (conf/get-param [:id :isbn :path]))
        :isbn
        (.startsWith id-uri (conf/get-param [:id :orcid :path]))
        :orcid
        (.startsWith id-uri (conf/get-param [:id :owner-prefix :path]))
        :owner-prefix
        (.startsWith id-uri (conf/get-param [:id :long-doi :path]))
        :long-doi
        (.startsWith id-uri (conf/get-param [:id :short-doi :path]))
        :short-doi
        (.startsWith id-uri (conf/get-param [:id :supplementary :path]))
        :supplementary
        (.startsWith id-uri (conf/get-param [:id :contributor :path]))
        :contributor
        :else
        :unknown))

