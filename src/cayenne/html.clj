(ns cayenne.html
  (:import [org.htmlcleaner SimpleHtmlSerializer HtmlCleaner])
  (:use [clojure.java.io :only [reader]])
  (:require [net.cgrand.enlive-html :as html]))

(defn fetch-url [url]
  (with-open [rdr (reader url)]
    (let [cleaner (HtmlCleaner.)
          props (.getProperties cleaner)
          serializer (SimpleHtmlSerializer. props)
          root-node (.clean cleaner rdr)]
      (-> (.getAsString serializer root-node)
          (java.io.StringReader.)
          (html/html-resource)))))
      