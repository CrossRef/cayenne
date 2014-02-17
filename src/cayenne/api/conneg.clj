(ns cayenne.api.conneg
  (:require [instaparse.core :as insta]
            [net.cgrand.enlive-html :as enlive]))

;; a simple accept header parser

(def accept-header-parser
  (insta/parser
   "ACCEPT = ENTRY+
    ENTRY = MEDIARANGE PARAM* <[#\",\\s*\"]>
    <MEDIARANGE> = '*' <'/'> '*' | (TYPE <'/'> '*') | (TYPE <'/'> SUBTYPE)
    PARAM = <#\";\\s*\"> KEY <'='> (VALUE | QUOTEDVALUE)
    <TOKEN> = #\"[\\w\\.\\-]+\"
    KEY = TOKEN
    VALUE = TOKEN
    TYPE = #\"\\w+\"
    SUBTYPE = #\"[\\w\\+\\.\\-]+\"
    QUOTEDVALUE = <'\"'> #\"[^\\\"]+\" <'\"'>"
   :output-format :enlive))

(defn make-accept [accept-value]
  (let [t (accept-header-parser accept-value)]
    (map #(hash-map
           :params (reduce
                    (fn [m pair]
                      (assoc m
                        (-> (enlive/select pair [:KEY enlive/text]) first keyword)
                        (first
                         (concat
                          (enlive/select pair [:VALUE enlive/text])
                          (enlive/select pair [:QUOTEDVALUE enlive/text])))))
                    {}
                    (enlive/select % [:PARAM]))
           :type (first (enlive/select % [:TYPE enlive/text]))
           :subtype (first (enlive/select % [:SUBTYPE enlive/text])))
         (enlive/select t [:ENTRY]))))

(defn make-sorted-accept [accept-value]
  (sort-by
   #(Float/parseFloat (or (get-in % [:params :q]) "1.0"))
   >
   (make-accept accept-value)))

;; a ring middleware to stick accept data structure in request object

(defn wrap-accept [handler]
  (fn [req]
    (let [accept-value (get-in req [:headers "accept"])]
      (if accept-value
        (handler (assoc req :accept (make-sorted-accept accept-value)))
        (handler req)))))
  
;; a libertor decision function that selects a most appropriate content type,
;; if any is available

(defn content-type-matches
  [cts]
  (fn [ctx]
    (let [client-acceptable-types (get-in ctx [:request :accept])
          type (first
                (filter
                 #(some #{(str (:type %) "/" (:subtype %))} cts)
                 client-acceptable-types))]
      (when type
        {:representation
         {:media-type (str (:type type) "/" (:subtype type))
          :parameters (:params type)}}))))
