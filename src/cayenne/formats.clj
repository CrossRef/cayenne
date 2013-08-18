(ns cayenne.formats
  (:require [cayenne.util :as util]))

(defrecord Metadata [format content])

(defmulti ->metadata (fn [source & more] source))
(defmulti ->format-name keyword)
(defmulti ->item-tree :format)
(defmulti ->format (fn [fmt _] fmt))

(defn make-metadata [format content] (Metadata. format content))

(defmethod ->metadata java.lang.String [s & {:keys [format]}]
  (make-metadata format s))

(defmethod ->metadata java.net.URL [url & {:keys [format] :or {format :guess}}]
  (make-metadata
   (if (= format :guess)
     (->format-name "application/vnd.crossref.unixref+xml")
     format)
   (slurp url)))

(defmethod ->metadata java.io.File [file & {:keys [format] :or {format :guess}}]
  (make-metadata
   (if (= format :guess)
     (->format-name (util/file-ext file))
     format)
   (slurp file)))

