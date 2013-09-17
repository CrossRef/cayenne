(ns cayenne.formats.unixsd
  (:require [cayenne.formats.unixref :as unixref]
            [cayenne.ids.prefix :as prefix]
            [cayenne.item-tree :as itree]
            [cayenne.util :as util]
            [cayenne.xml :as xml]
            [taoensso.timbre :as timbre :refer [error info]]))

(defn parse-publisher [oai-record]
  (let [meta-loc (xml/xselect1 oai-record :> "crossref_metadata")]
    (-> (itree/make-item :org)
        (itree/add-property :name (xml/xselect1 oai-record 
                                                :> "crm-item" 
                                                [:= "name" "publisher-name"] 
                                                :text))
        (itree/add-id (-> oai-record
                          (xml/xselect1 :> "crm-item"
                                        [:= "name" "owner-prefix"] :text)
                          (prefix/to-prefix-uri))))))

(defn parse-citation-count [oai-record]
  (-> oai-record 
      (xml/xselect1 :> "crm-item" [:= "name" "citedby-count"] :text)
      (util/parse-int)))

(defn unixsd-record-parser
  [oai-record]
  (let [result (unixref/unixref-record-parser oai-record)
        work (second result)
        primary-id (first result)]
    (-> work
        (itree/add-relation :publisher (parse-publisher oai-record))
        (itree/add-property :citation-count (parse-citation-count oai-record)))))
      
;; todo citation-count should be attached to item with primary-id, not tree root.

