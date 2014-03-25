(ns cayenne.api.deposit
  (:require [clojure.xml :refer xml])
  (:import [java.util UUID]))

;; handle deposit dispatch based on deposited content type

(defrecord DepositContext [content-type object batch-id])

(defn make-deposit-context [content-type object]
  (DepositContext.
   content-type
   object
   (UUID.)))

(defmulti deposit! :content-type)

(defmethod deposit! "application/vnd.crossref.deposit+xml" [context]
  (let [xml-object (-> context :object xml/parse)]
    
    
    
  ; parse xml
  ; replace batch-id
  ; pull out DOIs
  ; record in mongo
  ; deposit
  ())

(defmethod deposit! "application/vnd.crossref.partial+xml" [context]
  ())
  
