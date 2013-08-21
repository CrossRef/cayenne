(ns cayenne.formats.datacite
  (:require [cayenne.xml :as xml]
            [cayenne.conf :as conf]
            [clojure.string :as string])
  (:use [cayenne.ids.doi :only [to-long-doi-uri]]
        [cayenne.item-tree]))

(defn parse-primary-id [oai-record]
  (xml/xselect1 oai-record "identifier" :text))

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
        name-bits (string/split full-name #"\s+")]
    (-> (make-item :person)
        (add-property :first-name (string/join " " (rest name-bits)))
        (add-property :last-name (first name-bits)))))
        
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

(defn parse-record [oai-record]
  (-> (make-item :work (parse-resource-type oai-record))
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
