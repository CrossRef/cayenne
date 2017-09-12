(ns cayenne.api.v1.fields
  (:require [clojure.string :as str]))

(defn any-of [& fields]
  (fn [query-value]
    (let [field-strs (map #(str % ":(" query-value ")") fields)]
      (str "(" (str/join " OR " field-strs) ")"))))

(def work-fields
  {"bibliographic" (any-of "content_citation")
   "title" (any-of "hl_title" "hl_subtitle")
   "container-title" (any-of "hl_publication")
   "event-name" (any-of "event_name")
   "event-theme" (any-of "event_theme")
   "event-location" (any-of "event_location")
   "event-sponsor" (any-of "event_sponsor")
   "event-acronym" (any-of "event_acronym")
   "standards-body-name" (any-of "standards_body_name")
   "standards-body-acronym" (any-of "standards_body_acronym")
   "degree" (any-of "degree")
   "affiliation" (any-of "affiliation")
   "publisher-name" (any-of "publisher")
   "publisher-location" (any-of "publisher_location")
   "funder-name" (any-of "hl_funder_name")
   "author" (any-of "hl_authors")
   "editor" (any-of "hl_editors")
   "chair" (any-of "hl_chairs")
   "translator" (any-of "hl_translators")
   "contributor" (any-of "hl_authors"
                         "hl_editors"
                         "hl_chairs"
                         "hl_translators")})

(defn apply-field-queries [base-query field-queries]
  (if (empty? field-queries)
    base-query
    (let [field-query-parts
          (map #((work-fields (first %)) (second %)) field-queries)]
      (str "(" base-query ") AND " (str/join " AND " field-query-parts)))))
