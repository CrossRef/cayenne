(ns cayenne.tasks.prefix
  (:require [clj-http.client :as client]
            [cayenne.xml :as xml]
            [cayenne.conf :as conf]
            [clojure.core.memoize :as memoize])
  (:use [cayenne.util])
  (:import [java.io StringReader]))

;; todo unixref oai parser should extract owner prefix.

(defn parse-prefix-info [xml]
  (let [location (.trim (xml/xselect1 xml :> "publisher" "publisher_location" :text))]
    (-> {:name (xml/xselect1 xml :> "publisher" "publisher_name" :text)}
        (?> (not (empty? location)) :location location))))

(defn get-prefix-info [owner-prefix]
  (let [res (client/get (str (conf/get-param [:api :prefix-info-url]) owner-prefix))]
    (when (= 200 (:status res))
      (-> (:body res)
          (StringReader.)
          (xml/read-xml)
          (parse-prefix-info)))))

(def get-prefix-info-memo (memoize/memo-lru get-prefix-info))

(defn apply-to
  "Attach member information to an item via calls to gerPrefixPublisher. Responses
   are cached."
  [item]
  ())
