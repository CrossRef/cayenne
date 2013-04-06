(ns cayenne.ids.isbn)

(defn is-isbn?
  []
  ())

(defn extract-issn [] ())

(defn normalize-issn [] ())

(defn to-isbn-uri
  "Find anything in s that looks like it may be an ISBN and return it
  in a normalized URI form."
  [s]
  (str "http://id.crossref.org/isbn/" (normalize-isbn s)))
