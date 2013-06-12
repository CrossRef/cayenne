(ns cayenne.formats
  (require [cayenne.formats.unixref :as unixref]
           [cayenne.xml :as xml]))

(defn undefined []
  (throw (IllegalArgumentException. "Not supported.")))

(defprotocol Translator
  "Translate to and from string representations of a format / graftable data 
   structures."
  (parse [this object] "Parse the bare format into graftable clojure.")
  (write [this graftable] "Write out a graftable structure as format."))

(defprotocol XmlParser
  "Translate from an xml structure representing a format to graftable data."
  (parse-xml [this xml]))

(defprotocol JsonParser
  "Translate from a json structure representing a format to graftable data."
  (parse-json [this json]))

(defrecord Unixref []
  Translator XmlParser
  (parse [this s] (xml/process-xml (reader s) "doi_record" unixref-record-parser))
  (parse-xml [this xml] (unixref/unixref-record-parser xml))
  (write [this graftable] (undefined)))

(defrecord Bibjson []
  Translator JsonParser
  (parse [this object] (undefined))
  (parse-json [this json] (undefined))
  (write [this graftable] (undefined)))

(def translators
  {"application/vnd.crossref.unixref+xml" (Unixref.)
   "application/bibjson+json" (Bibjson.)})

