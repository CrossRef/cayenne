(ns cayenne.ids.isbn
  (:require [cayenne.conf :as conf]))

(defn is-isbn?
  [s]
  ())

(defn extract-isbn [s] s)

(defn normalize-isbn [s] 
  (extract-isbn s))

(defn to-isbn-uri
  "Find anything in s that looks like it may be an ISBN and return it
  in a normalized URI form."
  [s]
  (when s
    (str (conf/get-param [:id :isbn :path]) (normalize-isbn s))))
