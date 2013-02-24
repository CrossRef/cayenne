(ns cayenne.sources.wok
  (:require [net.cgrand.enlive-html :as html]))

(defn journal-name [full short]
  {:full-name (.replace (.trim full) \newline \space)
   :short-name (.replace (.trim short) \newline \space)})

(defn journal-names-scraper [html]
  (let [full-names (map html/text (html/select html [:dl :dt]))
        short-names (map html/text (html/select html [:dl :dd]))]
    (map journal-name full-names short-names)))

(def a-to-z (map char (range 65 91)))

(def journal-pages 
  (map 
   #(java.net.URL. 
     (str 
      "http://images.webofknowledge.com/WOK46/help/WOS/" 
      % 
      "_abrvjt.html")) 
   a-to-z))

; (use 'cayenne.sources.wok)
; (use 'cayenne.tasks)
; (scrape-urls journal-pages :scraper journal-names-scraper :task (record-writer "out.txt"))