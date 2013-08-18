(ns cayenne.formats.ris)

(defn subtype->ris-type [subtype]
  {:journal "JOUR"
   :article "JOUR"
   :journal-article "JOUR"
   :journal-issue "JOUR"
   :journal-volume "JOUR"
   :proceedings "CONF"
   :proceedings-article "CPAPER"
   :report "RPRT"
   :report-series "RPRT"
   :standard "STAND"
   :standard-series "STAND"
   :dataset "DATA"
   :edited-book "EDBOOK"
   :monograph "BOOK"
   :reference-book "BOOK"
   :chapter "CHAP"
   :section "CHAP"
   :part "CHAP"
   :track "CHAP"
   :reference-entry "CHAP"
   :dissertation "THES"
   :component "FIGURE"
   :image "DATA"
   :modal "DATA"
   :film "DATA"
   :other "GENERIC"})

(defn make-ris-type [subtype]
  ["TY" (subtype->ris-type subtype)])

(defn make-ris-ids [doi uri]
  ["DO" doi
   "UR" uri])

(defn make-ris-authors [authors]
  (flatten (map (["AU" %]) authors)))

(defmethod ->format [:ris primary-id item-tree]
  (let [primary-item (itree/find-item-with-id primary-id)
        doi-uri (first (itree/get-item-ids primary-item :doi))
        doi (doi/normalize-long-doi doi-uri)]
    (concat
     (make-ris-type (:subtype primary-item))
     (make-ris-ids doi doi-uri)
     (make-ris-authors ))))
     
(defmethod ->item-tree :ris [metadata]
  ())

(defmethod ->format-name "ris" :ris)
(defmethod ->format-name "application/x-research-info-systems" :ris)
