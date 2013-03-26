(ns cayenne.html
  (:import [org.htmlcleaner SimpleHtmlSerializer HtmlCleaner])
  (:use [clojure.java.io :only [reader]])
  (:require [net.cgrand.enlive-html :as html])
  (:use [clojure.java.io :only [file reader]])
  (:use [cayenne.job]))

(defn fetch-url [url]
  "Fetch the HTML content of a URL as an enlive html-resource."
  (with-open [rdr (reader url)]
    (let [cleaner (HtmlCleaner.)
          props (.getProperties cleaner)
          serializer (SimpleHtmlSerializer. props)
          root-node (.clean cleaner rdr)]
      (-> (.getAsString serializer root-node)
          (java.io.StringReader.)
          (html/html-resource)))))
      
; todo error reporting on pool-processing threads

(def debug-processing false)

(defn nothing [& rest] ())

(defn scrape-url [scraper-fn task-fn url]
  "Run a scraper and task on the result of a GET call for url."
  (task-fn (scraper-fn (fetch-url url))))

(defn scrape-url-in-pool [pool scraper-fn task-fn url]
  "Asynchronously run a scraper and task on the result of a GET call for url."
  (when debug-processing
    (prn (str "Executing " file)))
  (put-job #(scrape-url scraper-fn task-fn url)))

(defn scrape-urls [url-list & {:keys [count task scraper after before async]
                               :or {async true
                                    count :all
                                    task nothing 
                                    after nothing
                                    before nothing}}]
  "Invoke many scrape-url or scrape-url-in-pool calls, one for each url provided."
  (doseq [url url-list]
    (if async
      (scrape-url-in-pool processing-pool scraper task url)
      (scrape-url scraper task url))))

