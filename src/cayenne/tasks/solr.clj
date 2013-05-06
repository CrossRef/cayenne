(ns cayenne.tasks.solr
  (:use cayenne.item-tree))

(def pre-insert-cache (atom []))

(defn flush-insert-cache []
  ())

(defn as-solr-document [item]
  {"doi_key" (first (get-item-ids item :long-doi))
   "doi" (first (get-item-ids item :long-doi))
   "issn" (get-tree-ids item :issn)
   "isbn" (get-tree-ids item :isbn)
   ;; "funder" (-> item
   ;;              (get-tree-rel :funder)
   ;;              (partial (mapcat get-item-ids)))
   ;; "grant" (-> item
   ;;             (get-tree-rel :grant)
   ;;             (partial (mapcat get-item-ids)))
   "type" (get-item-type item)
   "subtype" (get-item-subtype item)})
  
(defn insert-item [item]
  (as-solr-document item))
  
  
