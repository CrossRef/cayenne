(ns cayenne.formats.rdf
  (:import [java.io StringWriter])
  (:require [cayenne.rdf :as rdf]
            [cayenne.ids.isbn :as isbn-id]
            [cayenne.ids.issn :as issn-id]
            [cayenne.ids.contributor :as contributor-id]))

;; TODO full-text links, funders, licenses

(defn make-rdf-issn-container [model metadata]
  (when (first (:ISSN metadata))
    (rdf/make-resource
     model
     (issn-id/to-issn-uri (first (:ISSN metadata)))
     (rdf/rdf model "type") (rdf/bibo-type model "Journal")
     (rdf/dct model "title") (first (:container-title metadata)))))

(defn make-rdf-isbn-container [model metadata]
  (when (first (:ISBN metadata))
    (rdf/make-resource
     model
     (isbn-id/to-isbn-uri (first (:ISBN metadata)))
     (rdf/rdf model "type") (rdf/bibo-type model "Book")
     (rdf/dct model "title") (first (:container-title metadata)))))

;; TODO for a make-rdf-doi-container would need container DOIs in solr 
;; and citeproc

(defn make-rdf-contributor [model doi contributor]
  (let [full-name (str (:given contributor) " " (:family contributor))]
    (rdf/make-resource
     model
     (contributor-id/to-contributor-id-uri full-name 0 doi)
     (rdf/rdf model "type") (rdf/foaf-type model "Person")
     (rdf/owl model "sameAs") (rdf/make-resource model (:ORCID contributor))
     (rdf/foaf model "givenName") (:given contributor)
     (rdf/foaf model "familyName") (:family contributor)
     (rdf/foaf model "name") full-name)))

(defn make-rdf-work [model metadata]
  (concat
   [(rdf/dct model "identifier") (:DOI metadata)
    (rdf/owl model "sameAs") (rdf/make-resource model (str "doi:" (:DOI metadata)))
    (rdf/owl model "sameAs") (rdf/make-resource model (str "info:doi/" (:DOI metadata)))
    (rdf/prism model "doi") (:DOI metadata)
    (rdf/bibo model "doi") (:DOI metadata)
    (rdf/prism model "volume") (:volume metadata)
    (rdf/bibo model "volume") (:volume metadata)
    (rdf/dct model "title") (first (:title metadata))
    (rdf/dct model "publisher") (:publisher metadata)
    (rdf/dct model "isPartOf") (make-rdf-issn-container model metadata)
    (rdf/dct model "isPartOf") (make-rdf-isbn-container model metadata)]
   (flatten
      (map #(vector (rdf/dct model "creator")
                    (make-rdf-contributor model (:DOI metadata) %))
           (concat
            (:author metadata)
            (:editor metadata)
            (:translator metadata)
            (:chair metadata))))))

(defn ->rdf-model [metadata]
  (let [model (rdf/make-model)
        properties (make-rdf-work model metadata)] 
    (apply rdf/make-resource model (:URL metadata) properties)
    model))

(defn ->rdf-xml [metadata]
  (let [writer (StringWriter.)]
    (.write (->rdf-model metadata) writer)
    (.toString writer)))

(defn ->n3 [metadata] ())

(defn ->n-triples [metadata] ())

(defn ->turtle [metadata] ())
