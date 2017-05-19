(ns cayenne.ids.isbn
  (:require [cayenne.ids :as ids]))

(defn is-isbn?
  [s]
  ())

(defn extract-isbn [s]
  (when s
    (or
     (nth (re-find #"^(http\:\/\/id\.crossref\.org\/isbn\/)(.*)" s) 2)
     s)))

(defn normalize-isbn [s] 
  (extract-isbn s))

(defn to-isbn-uri
  "Find anything in s that looks like it may be an ISBN and return it
  in a normalized URI form."
  [s]
  (when s
    (ids/get-id-uri :isbn (normalize-isbn s))))

