(ns cayenne.formats.citeproc
  (:require [clj-time.format :as df]
            [clj-time.core :as dt]
            [clj-time.coerce :as dc]
            [clojure.string :as string]
            [cayenne.ids.doi :as doi-id]
            [cayenne.ids.issn :as issn-id]
            [cayenne.ids.isbn :as isbn-id]))


;; todo currently this goes from solr doc to citeproc.
;; needs a rewrite since this differs from other formats which
;; go to/from item trees.

(defn ->date-parts
  ([year month day]
     (cond (and year month day)
           {:date-parts [[year, month, day]]}
           (and year month)
           {:date-parts [[year, month]]}
           :else
           {:date-parts [[year]]}))
  ([date-obj]
     (let [d (dc/from-date date-obj)]
       {:date-parts [[(dt/year d) (dt/month d) (dt/day d)]]
        :timestamp (dc/to-long d)})))
        
(defn ->citeproc-contrib [name & orcid]
  (let [base {:literal name}]
    (if orcid
      (assoc base :ORCID orcid)
      base)))

(defn ->citeproc-contribs [solr-doc k]
  (let [contribs (string/split (get solr-doc k) #", ")]
    (map ->citeproc-contrib contribs)))

(defn ->citeproc-licenses [solr-doc]
  (map #(if %2 {:URL %1 :start %2} {:URL %1})
       (get solr-doc "license_url")
       (get solr-doc "license_start")))

(defn ->citeproc-links [solr-doc]
  (map #(if %2 {:URL %1 :content-type %2} {:URL %1})
       (get solr-doc "full_text_url")
       (get solr-doc "full_text_type")))

(defn ->citeproc [solr-doc]
  {:source (get solr-doc "source")
   :volume (get solr-doc "hl_volume")
   :issue (get solr-doc "hl_issue")
   :prefix (get solr-doc "owner_prefix")
   :DOI (doi-id/extract-long-doi (get solr-doc "doi"))
   :URL (get solr-doc "doi")
   :ISBN (map isbn-id/extract-isbn (get solr-doc "isbn"))
   :ISSN (map issn-id/extract-issn (get solr-doc "issn"))
   :title (get solr-doc "hl_title")
   :container-title (get solr-doc "hl_publication")
   :issued (->date-parts (get solr-doc "year") 
                         (get solr-doc "month") 
                         (get solr-doc "day"))
   :deposited (->date-parts (get solr-doc "deposited_at"))
   :indexed (->date-parts (get solr-doc "indexed_at"))
   :author (->citeproc-contribs solr-doc "hl_authors")
   :editor (->citeproc-contribs solr-doc "hl_editors")
   :chair (->citeproc-contribs solr-doc "hl_chairs")
   :contributor (->citeproc-contribs solr-doc "hl_contributors")
   :translator (->citeproc-contribs solr-doc "hl_translators")
   :publisher (get solr-doc "publisher")
   :page (str (get solr-doc "hl_first_page")
              "-" 
              (get solr-doc "hl_last_page"))
   :type (get solr-doc "type")
   :subject (get solr-doc "category")
   :archive (get solr-doc "archive")
   :license (->citeproc-licenses solr-doc)
   :link (->citeproc-links solr-doc)
   :score (get solr-doc "score")})
