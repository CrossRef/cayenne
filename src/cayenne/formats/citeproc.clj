(ns cayenne.formats.citeproc
  (:require [clj-time.format :as df]
            [clj-time.core :as dt]
            [clj-time.coerce :as dc]
            [clojure.string :as string]
            [cayenne.util :as util]
            [cayenne.ids.doi :as doi-id]
            [cayenne.ids.issn :as issn-id]
            [cayenne.ids.isbn :as isbn-id]))

;; todo currently this goes from solr doc to citeproc.
;; needs a rewrite since this differs from other formats which
;; go to/from item trees.

(defn ?> [m key value]
  (cond (= (type value) java.lang.String)
        (if (clojure.string/blank? value)
          m
          (assoc m key value))
        (seq? value)
        (if (empty? value)
          m
          (assoc m key value))
        (nil? value)
        m
        :else
        (assoc m key value)))

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
        
(defn license [url start-date delay-in-days content-version]
  (-> {:URL url}
      (?> :start (->date-parts start-date))
      (?> :delay-in-days delay-in-days)
      (?> :content-version content-version)))

(defn ->citeproc-licenses [solr-doc]
  (map license
       (get solr-doc "license_url")
       (get solr-doc "license_start")
       (get solr-doc "license_delay")
       (get solr-doc "license_version")))

(defn link [url content-type content-version]
  (-> {:URL url}
      (?> :content-type content-type)
      (?> :content-version content-version)))

(defn ->citeproc-links [solr-doc]
  (map link
       (get solr-doc "full_text_url")
       (get solr-doc "full_text_type")
       (get solr-doc "full_text_version")))

(defn ->citeproc-pages [solr-doc]
  (let [first-page (get solr-doc "hl_first_page")
        last-page (get solr-doc "hl_last_page")]
    (cond (and (not (clojure.string/blank? last-page))
               (not (clojure.string/blank? first-page)))
          (str first-page "-" last-page)
          (not (clojure.string/blank? first-page))
          first-page
          :else
          nil)))

(defn contrib 
  "Drop placeholders indicating missing data."
  [type orcid suffix given family]
  (let [has-type? (not= type "-")
        has-orcid? (not= orcid "-")
        has-suffix? (not= suffix "-")
        has-given? (not= given "-")
        has-family? (not= family "-")]
    (-> {}
        (util/?> has-type? assoc :type type)
        (util/?> has-orcid? assoc :ORCID orcid)
        (util/?> has-suffix? assoc :suffix suffix)
        (util/?> has-given? assoc :given given)
        (util/?> has-family? assoc :family family))))

(defn ->citeproc-contribs [solr-doc]
  (reduce #(conj %1 {(get %2 :type) (dissoc %2 :type)})
          {}
          (map contrib
               (get solr-doc "contributor_type")
               (get solr-doc "contributor_orcid")
               (get solr-doc "contributor_suffix")
               (get solr-doc "contributor_given_name")
               (get solr-doc "contributor_family_name"))))

(defn ->citeproc-funders [solr-doc]
  (map #(hash-map :DOI (doi-id/extract-long-doi %)) (get solr-doc "funder_doi")))

(defn ->citeproc [solr-doc]
  (-> {:source (get solr-doc "source")
       :prefix (get solr-doc "owner_prefix")
       :DOI (doi-id/extract-long-doi (get solr-doc "doi"))
       :URL (get solr-doc "doi")
       :issued (->date-parts (get solr-doc "year")
                             (get solr-doc "month")
                             (get solr-doc "day"))
       :deposited (->date-parts (get solr-doc "deposited_at"))
       :indexed (->date-parts (get solr-doc "indexed_at"))
       :publisher (get solr-doc "publisher")
       :type (get solr-doc "type")
       :score (get solr-doc "score")}
      (?> :volume (get solr-doc "hl_volume"))
      (?> :issue (get solr-doc "hl_issue"))
      (?> :ISBN (map isbn-id/extract-isbn (get solr-doc "isbn")))
      (?> :ISSN (map issn-id/extract-issn (get solr-doc "issn")))
      (?> :title (set (get solr-doc "hl_title")))
      (?> :container-title (set (get solr-doc "hl_publication")))
      (?> :subject (get solr-doc "category"))
      (?> :archive (get solr-doc "archive"))
      (?> :license (->citeproc-licenses solr-doc))
      (?> :link (->citeproc-links solr-doc))
      (?> :page (->citeproc-pages solr-doc))
      (?> :funder (->citeproc-funders solr-doc))
      (merge (->citeproc-contribs solr-doc))))
