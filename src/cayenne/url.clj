(ns cayenne.url
  (:import [java.net URI])
  (:require [cayenne.util :refer [?>]]
            [clj-http.client :as client]
            [cayenne.conf :as conf]
            [clojure.string :as string]))

(def end-bits "(\\)\\.|>\\.|\\]\\.|\\}\\.|\\),|>,|\\],|\\},|\\)|,|\\.|>|\\]|\\})$")

(def tlds
  (let [domains (-> (conf/get-resource :tld-list)
                    (slurp)
                    (string/split #"\s+"))
        clean-domains (map #(string/replace % #"^\." "") domains)]
    (reduce 
     (fn [m kv] (assoc-in m (first kv) {}))
     {}
     (for [d clean-domains] [(reverse (string/split d #"\.")) d]))))

(defn extract-tld* [remaining-parts tld-parts]
  (let [next-test (conj tld-parts (first remaining-parts))]
    (if-not (get-in tlds next-test)
      tld-parts
      (recur (rest remaining-parts) next-test))))

(defn extract-tld [url]
  (let [host (.getHost (URI/create url))
        host-parts (reverse (string/split host #"\."))]
    (string/join "." (reverse (extract-tld* host-parts [])))))

(defn extract-root [url]
  (let [tld (extract-tld url)
        host (.getHost (URI/create url))
        without-tld (string/replace host (re-pattern (str tld "$")) "")]
    (second (re-find #"\.?([^\.]+)\.$" without-tld))))

(defn extract-one [text]
  (when-let [url (re-find #"https?:\/\/[^\s]+" text)]
    (.replaceFirst url end-bits "")))

(defn valid? [url-text]
  (try (do (URI/create url-text) true) (catch Exception e false)))

(defn resolves?
  "Tries to resolve a URL. Will return false if the connection
   times out, the host is not accessible, the server returns a non-ok
   HTTP status code or there are too many redirects."
  [url-text]
  (try
    (let [resp (client/get url-text {:socket-timeout 30000
                                     :conn-timeout 30000
                                     :max-redirects 10})]
      (if (client/success? resp) true false))
    (catch Exception e false)))

(defn locate
  "Locate a URL in text and try to resolve it. Reports the response
   as :good if between 200 and 399 or :bad otherwise. Also finds the host
   type of the URL, and repots whether or not the URL extracted is valid."
  [text]
  (when-let [clean-url (extract-one text)]
    (let [valid (valid? clean-url)]
      (if valid
        {:url clean-url
         :valid valid
         :resolves (resolves? clean-url)
         :root (extract-root clean-url)
         :tld (extract-tld clean-url)}
        {:url clean-url
         :valid valid
         :resolves false}))))

(defn locate-without-resolve
  "Like locate, but does not resolve a found url."
  [text]
    (when-let [clean-url (extract-one text)]
      (let [valid (valid? clean-url)]
        (if valid
          {:url clean-url
           :valid valid
           :root (extract-root clean-url)
           :tld (extract-tld clean-url)}
          {:url clean-url
           :valid valid}))))

