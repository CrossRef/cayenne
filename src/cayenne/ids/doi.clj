(ns cayenne.ids.doi
  (:require [clojure.string :as string]
            [cayenne.ids :as ids]))

(def funder-prefix "10.13039")

(defn is-long-doi?
  "Return true if s is a valid long DOI handle without URI prefix."
  [s]
  (not (nil? (re-find #"\A10\.[0-9]{4,}/[^\s]+\Z" s))))

(defn is-short-doi?
  "Return true if s is a valid short DOI handle without URI prefix."
  [s]
  (not (nil? (re-find #"\A10/[a-z0-9]+\Z" s))))

(defn is-any-doi? [s]
  (or (is-short-doi? s) (is-long-doi? s)))  

(defn extract-long-doi
  "Attempt to extract a DOI from the forms:
   http://dx.doi.org/<DOI>
   dx.doi.org/<DOI>
   doi:<DOI>
   <DOI>"
  [s]
  (or (re-find #"10\.[0-9]{4,}/[^\s]+" (or s "")) ""))

(defn extract-long-prefix [s]
  (first (string/split (extract-long-doi s) #"/")))

(defn extract-long-suffix [s]
  (string/join "/" (rest (string/split (extract-long-doi s) #"/"))))

(defn extract-short-doi
  "Attempt to extract a short DOI from the forms:
   http://doi.org/<SHORT_DOI_SUFFIX>
   doi.org/<SHORT_DOI_SUFFIX>
   <SHORT_DOI>"
  [s]
  (let [match (re-find #"doi\.org/([a-zA-Z0-9]+)" s)]
    (if match
      (str "10/" (nth match 1))
      (re-find #"10/[a-zA-Z0-9]+" s))))

(defn normalize-long-doi [s]
  (when s (.toLowerCase (extract-long-doi s))))

(defn normalize-short-doi [s]
  (when s (.toLowerCase (extract-short-doi s))))

;; Regex below is a hack to handle broken CrossRef DOIs that contain
;; non printable characters.
(defn to-long-doi-uri 
  "Ensure a long DOI is in a normalized URI form."
  [s]
  (when s
    (let [normalized-doi (-> (.replaceAll s "[^\\p{Print}]" "")
                             (normalize-long-doi))]
      (if (string/blank? normalized-doi)
        nil
        (ids/get-id-uri :long-doi normalized-doi)))))
 
(defn to-short-doi-uri 
  "Ensure a short DOI is in a normalized URI form."
  [s]
  (when s
    (ids/get-id-uri :short-doi (normalize-short-doi s))))

(defn with-prefix [doi prefix]
  (->> doi
       extract-long-suffix
       (str prefix "/")))

(defn with-funder-prefix [doi]
  (with-prefix doi funder-prefix))

