(ns cayenne.formats.unixsd
  (:require [cayenne.formats.unixref :as unixref]
            [cayenne.ids.prefix :as prefix]
            [cayenne.ids.member :as member-id]
            [cayenne.item-tree :as itree]
            [cayenne.util :as util]
            [cayenne.xml :as xml]
            [cayenne.ids.doi :as doi-id]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as str]
            [taoensso.timbre :as timbre :refer [error info]]))

(defn parse-crm-item-date [s]
  (when-not (str/blank? s)
    (let [d (tf/parse (tf/formatters :date-time-no-ms) s)]
      {:type :date
       :year (t/year d)
       :month (t/month d)
       :day (t/day d)
       :hour (t/hour d)
       :minute (t/minute d)
       :second (t/second d)})))

(defn parse-publisher [oai-record]
  (let [meta-loc (xml/xselect1 oai-record :> "crossref_metadata")]
    (-> (itree/make-item :org)
        (itree/add-property :name (xml/xselect1 oai-record 
                                                :> "crm-item" 
                                                [:= "name" "publisher-name"] 
                                                :text))
        (itree/add-id (-> oai-record
                          (xml/xselect1 :> "crm-item"
                                        [:= "name" "member-id"] :text)
                          member-id/to-member-id-uri))
        (itree/add-id (-> oai-record
                          (xml/xselect1 :> "crm-item"
                                        [:= "name" "owner-prefix"] :text)
                          (prefix/to-prefix-uri))))))

(defn parse-citation-count [oai-record]
  (-> oai-record 
      (xml/xselect1 :> "crm-item" [:= "name" "citedby-count"] :text)
      (util/parse-int-safe)))

(defn parse-created-date [oai-record]
  (-> oai-record
      (xml/xselect1 :> "crm-item" [:= "name" "created"] :text)
      parse-crm-item-date))

(defn parse-updated-date [oai-record]
  (-> oai-record
      (xml/xselect1 :> "crm-item" [:= "name" "last-update"] :text)
      parse-crm-item-date))

(defn parse-doi [oai-record]
  (-> oai-record
      (xml/xselect1 :> "query" "doi" :text)
      (doi-id/to-long-doi-uri)))

(defn insert-crm-publisher
  "Insert crm item publisher info, but do not overwrite publisher name and
   location if they are specified in the metadata body."
  [work oai-record]
  (let [crm-publisher-info (parse-publisher oai-record)
        body-publisher-info (-> work (itree/get-tree-rel :publisher) first)]
    (-> work
        (itree/delete-relation :publisher)
        (itree/add-relation
         :publisher
         (cond-> crm-publisher-info
           (:name body-publisher-info)
           (assoc :name (:name body-publisher-info))
           (:location body-publisher-info)
           (assoc :location (:location body-publisher-info)))))))

(defn unixsd-record-parser
  [oai-record]
  (let [result (unixref/unixref-record-parser oai-record)
        work (second result)
        primary-id (first result)]
    [(if primary-id
       primary-id
       (parse-doi oai-record))
     (-> work
         (insert-crm-publisher oai-record)
         (itree/delete-relation :deposited)
         (itree/add-relation :deposited (parse-updated-date oai-record))
         (itree/add-relation :first-deposited (parse-created-date oai-record))
         (itree/add-relation :cited-count (parse-citation-count oai-record)))]))
      
;; todo citation-count should be attached to item with primary-id, not tree root.

