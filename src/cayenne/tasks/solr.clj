(ns cayenne.tasks.solr
  (:use cayenne.item-tree))

(def pre-insert-cache (atom []))

(defn flush-insert-cache []
  ())

(defn get-categories [item]
  (if-let [journal (find-item-of-subtype item :journal)]
    (or (:category journal) [])
    []))

(defn get-earliest-pub-date [item]
  ())

(defn as-solr-document [item]
  {"doi_key" (first (get-item-ids item :long-doi))
   "doi" (first (get-item-ids item :long-doi))
   "issn" (get-tree-ids item :issn)
   "isbn" (get-tree-ids item :isbn)
   "category" (get-categories item)
   "type" (get-item-type item)
   "subtype" (get-item-subtype item)
   "hl_first_page" (:first-page item)
   "hl_last_page" (:last-page item)
   "hl_funder_name" (map :name (get-tree-rel item :funder))
   "hl_grant" (mapcat get-item-ids (get-tree-rel item :grant))})
     
(defn insert-item [item]
  (as-solr-document item))
  
  
