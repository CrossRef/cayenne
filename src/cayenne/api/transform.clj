(ns cayenne.api.transform
  (:require [cayenne.util :as util]
            [cayenne.conf :as conf]
            [cayenne.formats.rdf :as rdf]
            [cayenne.formats.ris :as ris]
            [cayenne.formats.citation :as citation]
            [cayenne.formats.bibtex :as bibtex]
            [clojure.data.json :as json]
            [org.httpkit.client :as hc]))

(def legacy-styles
  {"mla" "modern-language-association"
   "harvard3" "harvard1"})

(def csl-type
  {:journal-article :article-journal
   :book-chapter :chapter
   :posted-conent :manuscript
   :proceedings-article :paper-conference})

(defmulti ->format :media-type)

(defmethod ->format "text/turtle" [representation metadata]
  (rdf/->turtle metadata))

(defmethod ->format "text/n3" [representation metadata]
  (rdf/->n3 metadata))

(defmethod ->format "text/n-triples" [representation metadata]
  (rdf/->n-triples metadata))

(defmethod ->format "application/rdf+xml" [representation metadata]
  (rdf/->xml metadata))

(defmethod ->format "application/vnd.citationstyles.csl+json" [representation metadata]
  (cond-> metadata
    (-> metadata :type csl-type nil? not)
    (assoc :type (csl-type (:type metadata)))
        
    (not (empty? (:title metadata)))
    (assoc :title (first (:title metadata)))

    (not (empty? (:container-title metadata)))
    (assoc :container-title (first (:container-title metadata)))

    (not (empty? (:short-container-title metadata)))
    (assoc :container-title-short (first (:short-container-title metadata)))

    (not (empty? (:event metadata)))
    (assoc :event (get-in metadata [:event :name]))

    :always
    (dissoc :short-container-title)

    :always
    (dissoc :archive)

    :always
    (dissoc :issn-type)

    :always
    json/write-str))

(defmethod ->format "application/x-research-info-systems" [representation metadata]
  (ris/->ris metadata))

(defmethod ->format "text/x-bibliography" [representation metadata]
  (let [args (concat
              (when-let [style (get-in representation [:parameters :style])]
                [:style (or (legacy-styles style) style)])
              (when-let [lang (get-in representation [:parameters :locale])]
                [:language lang])
              (when-let [format (get-in representation [:parameters :format])]
                [:format format]))]
    (apply citation/->citation metadata args)))

(defmethod ->format "application/x-bibtex" [representation metadata]
  (bibtex/->bibtex metadata))

;; legacy formats 

(defmethod ->format "text/bibliography" [representation metadata]
  (->format (assoc representation :media-type "text/x-bibliography")
            metadata))

(defmethod ->format "application/citeproc+json" [representation metadata]
  (->format (assoc representation :media-type "application/vnd.citationstyles.csl+json")
            metadata))

(defmethod ->format "application/json" [representation metadata]
  (->format (assoc representation :media-type "application/vnd.citationstyles.csl+json")
            metadata))

(defmethod ->format "application/unixref+xml" [representation metadata]
  (->format (assoc representation :media-type "application/vnd.crossref.unixref+xml")
            metadata))

(defmethod ->format "text/plain" [representation metadata]
  (->format (assoc representation :media-type "text/x-bibliography")
            metadata))

;; for now we retrieve original unixref and unixsd, but in future perhaps we
;; will generate from citeproc

(defmethod ->format "application/vnd.crossref.unixref+xml" [representation metadata]
  (-> (str (conf/get-param [:upstream :unixref-url]) (:DOI metadata))
      (hc/get {:timeout 4000})
      (deref)
      (:body)))

(defmethod ->format "application/vnd.crossref.unixsd+xml" [representation metadata]
  (-> (str (conf/get-param [:upstream :unixsd-url]) (:DOI metadata))
      (hc/get {:timeout 4000})
      (deref)
      (:body)))
