(ns cayenne.formats.datacite
  (:require [cayenne.xml :as xml]
            [cayenne.conf :as conf]
            [cayenne.item-tree :refer :all :as t]
            [cayenne.util :refer [?>]]
            [cayenne.ids.doi :as doi-id]
            [cayenne.ids.orcid :as orcid-id]
            [clojure.string :as string]))

(defn parse-primary-id [oai-record]
  (doi-id/to-long-doi-uri (xml/xselect1 
                           oai-record 
                           "identifier"
                           [:= "identifierType" "DOI"]
                           :text)))

(defn parse-language [oai-record]
  (xml/xselect oai-record "language" :text))

(defn parse-title [title-loc]
  (let [subtype (if (= (xml/xselect1 title-loc ["titleType"]) "Subtitle")
                  :secondary :long)]
    (-> (make-item :title subtype)
        (add-property :value (xml/xselect1 title-loc :text)))))

(defn parse-titles [oai-record]
  (map parse-title (xml/xselect oai-record "titles" "title")))

(defn parse-publisher [oai-record]
  (-> (make-item :org)
      (add-property :name (xml/xselect1 oai-record "publisher" :text))))

;; todo contributor ids
(defn parse-contributor [contributor-loc type]
  (let [full-name (xml/xselect1 contributor-loc (str type "Name") :text)
        orcid (xml/xselect1 contributor-loc "nameIdentifier" 
                            [:= "nameIdentifierScheme" "ORCID"] :text)]
    (-> (make-item :person)
        (?> orcid add-id (orcid-id/to-orcid-uri orcid)) 
        (add-property :name full-name))))
        
(defn parse-contributors [oai-record type]
  (map parse-contributor 
       (xml/xselect oai-record (str type "s") type)
       (repeatedly (constantly type))))

(defn parse-pub-date [oai-record]
  (-> (make-item :date)
      (add-property :year (xml/xselect1 oai-record "publicationYear" :text))))

(defn parse-resource-type [oai-record]
  (let [general-type (xml/xselect1 oai-record "resourceType" ["resourceTypeGeneral"])
        specific-type (xml/xselect1 oai-record "resourceType" :text)]
    (cond
     (= general-type "Image")
     :image
     (and (= general-type "Text") (= specific-type "Article"))
     :article
     (and (= general-type "Event") (= specific-type "Conference presentation"))
     :proceedings-article
     (= general-type "Model")
     :model
     (= general-type "Film")
     :film
     (= general-type "Dataset")
     :dataset
     :else
     :other)))

(defn parse-doi-relation [related-doi-loc]
  (-> (make-item :rel)
      (add-property :value (-> related-doi-loc 
                               (xml/xselect1 :text) 
                               doi-id/to-long-doi-uri))
      (add-property :rel-type (-> related-doi-loc
                                  (xml/xselect1 ["relationType"])))))

(defn parse-relations [oai-record]
  (let [related-doi-locs (xml/xselect oai-record 
                                      "relatedIdentifiers"
                                      "relatedIdentifier"
                                      [:= "relatedIdentifierType" "DOI"])]
    (map parse-doi-relation related-doi-locs)))

(defn parse-record [oai-record]
  (-> (make-item :work (parse-resource-type oai-record))
      (add-id (parse-primary-id oai-record))
      (add-relations :rel (parse-relations oai-record))
      (add-relations :title (parse-titles oai-record))
      (add-relations :author (parse-contributors oai-record "creator"))
      (add-relations :contributor (parse-contributors oai-record "contributor"))
      (add-relation :published-online (parse-pub-date oai-record))
      (add-relation :publisher (parse-publisher oai-record))))

(defn datacite-record-parser
  [oai-record]
  [(parse-primary-id oai-record)
   (parse-record oai-record)])

;(defmethod ->format-name "datacite-xml" :datacite)
;(defmethod ->format-name "application/vnd.datacite+xml" :datacite)

;; todo for both datacite and unixref
;; record source (cr or datacite)
;; record last update time (deposit time)
