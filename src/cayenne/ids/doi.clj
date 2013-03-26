(ns cayenne.ids.doi)

(defn is-long-doi? [s]
  "Return true if s is a valid long DOI handle."
  (not (nil? (re-find #"\A10\.[0-9]{4,}/[^\s]+\Z" s))))

(defn is-short-doi? [s]
  "Return true if s is a valid short DOI handle."
  (not (nil? (re-find #"\A10/[a-z0-9]+\Z" s))))

(defn is-any-doi? [s]
  (or (is-short-doi? s) (is-long-doi? s)))

(defn extract-long-doi [s]
  "Attempt to extract a DOI from the forms:
   http://dx.doi.org/<DOI>
   dx.doi.org/<DOI>
   doi:<DOI>
   <DOI>"
  (re-find #"10\.[0-9]{4,}/[^\s]+" s))

(defn extract-short-doi [s]
  "Attempt to extract a short DOI from the forms:
   http://doi.org/<SHORT_DOI_SUFFIX>
   doi.org/<SHORT_DOI_SUFFIX>
   <SHORT_DOI>"
  (let [match (re-find #"doi\.org/([a-zA-Z0-9]+)" s)]
    (if match
      (str "10/" (nth match 1))
      (re-find #"10/[a-zA-Z0-9]+" s))))

(defn normalize-long-doi [s]
  (when s (.toLowerCase (extract-long-doi s))))

(defn normalize-short-doi [s]
  (when s (.toLowerCase (extract-short-doi s))))
