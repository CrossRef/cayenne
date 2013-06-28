(ns cayenne.tasks.solr
  (:use cayenne.item-tree)
  (:import [org.apache.solr.common SolrInputDocument]
           [org.apache.solr.client.solrj.request CoreAdminRequest]
           [org.apache.solr.common.params CoreAdminParams$CoreAdminAction])
  (:require [clojure.string :as string]
            [cayenne.conf :as conf]
            [cayenne.ids :as ids]))

(def insert-list-max-size 50000)
(def insert-list (ref []))

(defn flush-insert-list []
  (dosync
   (when-not (empty? @insert-list)
     (.add (conf/get-service :solr-update) @insert-list)
     (alter insert-list (constantly [])))))

(defn clear-insert-list []
  (dosync
   (alter insert-list (constantly []))))

(defn flush-commit-swap []
  (conf/log "Final solr insert list flush...")
  (flush-insert-list)
  (conf/log "Performing a SOLR commit...")
  (.commit (conf/get-service :solr-update) true true)
  (conf/log "Swapping SOLR cores...")
  (doto (CoreAdminRequest.)
    (.setCoreName (conf/get-param [:service :solr :insert-core]))
    (.setOtherCoreName (conf/get-param [:service :solr :query-core]))
    (.setAction CoreAdminParams$CoreAdminAction/SWAP)
    (.process (conf/get-service :solr))))

(defn get-categories [item]
  (if-let [journal (find-item-of-subtype item :journal)]
    (or (:category journal) [])
    []))

(defn get-preferred-pub-date [item]
  (or
   (first (get-tree-rel item :published-print))
   (first (get-tree-rel item :published-online))))

(defn get-contributor-orcids [item]
  (let [contributors (mapcat #(get-item-rel item %) contributor-rels)]
    (filter (complement nil?) (mapcat :id contributors))))

(defn initials [first-name]
  (when first-name
    (string/join " " (map first (string/split first-name #"[\s\-]+")))))

(defn as-name [org-or-person]
  (cond (= :org (get-item-type org-or-person))
        (:name org-or-person)
        (= :person (get-item-type org-or-person))
        (str (:first-name org-or-person) " " (:last-name org-or-person))))

(defn as-initials [org-or-person]
  (cond (= :org (get-item-type org-or-person))
        (as-name org-or-person)
        (= :person (get-item-type org-or-person))
        (str (initials (:first-name org-or-person)) " " (:last-name org-or-person))))

(defn get-contributor-names
  "Contributor names as a concatenated string."
  [item type]
  (let [contributors (get-item-rel item type)]
    (string/join ", " (map as-name contributors))))

(defn get-primary-author [item]
  (first (get-item-rel item :author)))

(defn get-contributors [item]
  (mapcat (partial get-item-rel item) contributor-rels)) ;; todo deal with orgs

(defn get-container-titles [item]
  (let [titles (get-descendant-rel item :title)]
    (map :value titles)))

(defn get-oa-status [item]
  (let [journal (find-item-of-subtype item :journal)]
    (or (:oa-status journal) "Other")))

(defn as-solr-base-field [item]
  (string/join 
   " "
   (-> []
       (conj (:year (get-preferred-pub-date item))) ; year
       (conj (:issue (find-item-of-subtype item :journal-issue))) ; issue
       (conj (:volume (find-item-of-subtype item :journal-volume))) ; volume
       (conj (:first-page item)) ; pages
       (conj (:last-page item)) ; pages
       (concat (map :value (get-item-rel item :title))) ; work titles
       (concat (get-container-titles item))))) ; publication titles

(defn as-solr-citation-field [item]
  (string/join
   " "
   (-> [(as-solr-base-field item)]
       (concat (map as-initials (get-contributors item)))))) ; names with initials

(defn as-solr-content-field [item]
  (string/join
   " "
   (-> [(as-solr-base-field item)]
       (concat (map ids/extract-supplementary-id (get-tree-ids item :supplementary))) ; plain supp ids
       (concat (map as-name (get-contributors item))) ; full names
       (concat (mapcat get-item-ids (get-tree-rel item :awarded))) ; grant numbers
       (concat (map :name (get-tree-rel item :funder)))))) ; funder names

(defn as-grant-map [item]
  (letfn [(combine [memo nxt]
            (let [funder-name (:name nxt)
                  awards (or (get memo funder-name) [])
                  new-awards (mapcat :id (get-item-rel nxt :awarded))]
              (assoc memo funder-name (concat awards new-awards))))]
    (reduce combine {} (get-tree-rel item :funder))))

(defn as-solr-grant-info-field [item]
  (letfn [(funder-info [funder-name award-ids]
              (str
               funder-name
               " "
               (if-not (empty? award-ids)
                 (str "(" (string/join ", " award-ids) ")")
                 "")))]
    (string/join " | " (for [[k v] (as-grant-map item)] (funder-info k v)))))

(defn as-solr-document [item]
  (let [grant-map (as-grant-map item)
        funder-names (set (map :name (get-tree-rel item :funder)))
        funder-dois (set (mapcat :id (get-tree-rel item :funder)))
        pub-date (get-preferred-pub-date item)
        primary-author (get-primary-author item)
        container-titles (get-container-titles item)]
    {"source" (:source item)
     "doi_key" (first (get-item-ids item :long-doi))
     "doi" (first (get-item-ids item :long-doi))
     "issn" (get-tree-ids item :issn)
     "isbn" (get-tree-ids item :isbn)
     "supplementary_id" (get-tree-ids item :supplementary)
     "orcid" (get-contributor-orcids item)
     "category" (get-categories item)
     "funder_name" funder-names
     "funder_doi" funder-dois
     "type" (subtype-labels (get-item-subtype item))
     "first_author_given" (:first-name primary-author)
     "first_author_surname" (:last-name primary-author)
     "content" (as-solr-content-field item)
     "content_citation" (as-solr-citation-field item)
     "publication" container-titles
     "oa_status" (get-oa-status item)
     "hl_publication" container-titles
     "year" (:year pub-date)
     "month" (:month pub-date)
     "day" (:day pub-date)
     "hl_year" (:year pub-date)
     "hl_authors" (get-contributor-names item :author)
     "hl_editors" (get-contributor-names item :editor)
     "hl_chairs" (get-contributor-names item :chair)
     "hl_translators" (get-contributor-names item :translator)
     "hl_contributors" (get-contributor-names item :contributor)
     "hl_first_page" (:first-page item)
     "hl_last_page" (:last-page item)
     "hl_funder_name" funder-names
     "hl_grant" (as-solr-grant-info-field item)
     "hl_issue" (:issue (find-item-of-subtype item :journal-issue))
     "hl_volume" (:volume (find-item-of-subtype item :journal-volume))
     "hl_title" (map :value (get-item-rel item :title))}))

(defn as-solr-input-document [solr-map]
  (let [doc (SolrInputDocument.)]
    (doseq [[k v] solr-map]
      (.addField doc k v))
    doc))
     
(defn insert-item [item]
  (let [solr-map (as-solr-document item)]
    (when (get solr-map "doi_key")  ;; todo debug output for fail case
      (let [solr-doc (as-solr-input-document solr-map)]
        (dosync
         (alter insert-list conj solr-doc)
         (when (> (count @insert-list) insert-list-max-size)
           (flush-insert-list)))))))
  
