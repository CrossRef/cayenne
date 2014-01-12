(ns cayenne.formats.ris
  (:require [clojure.string :as string]))

; Convert extended citeproc to RIS.

(def subtype->ris-type
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
  (when subtype
    ["TY" (or (subtype->ris-type (keyword subtype)) "GENERIC")]))

(defn make-ris-ids [doi uri]
  (concat
   (when doi ["DO" doi])
   (when uri ["UR" uri])))

(defn make-ris-authors [authors]
  (when authors
    (flatten 
     (map #(vector "AU" (str 
                         (:family %) 
                         (when (:given %) (str ", " (:given %)))))
          authors))))

(defn make-ris-titles [title container-title]
  (concat
   (when title ["TI" title])
   (when container-title ["T2" container-title])))

(defn make-ris-pub-date [issued]
  (let [year (get-in issued [:date-parts 0 0])
        month (get-in issued [:date-parts 0 1])
        day (get-in issued [:date-parts 0 2])]
    (concat 
     (when year
       ["PY" year])
     (cond 
      (and year month day)
      ["DA" (str year "/" (format "%02d" month) "/" (format "%02d" day))]
      (and year month)
      ["DA" (str year "/" (format "%02d" month))]))))

(defn make-ris-pages [pages]
  (when pages
    ["SP" pages]))

(defn make-ris-end []
  ["ER" \newline])

(defn make-ris-publisher [publisher]
  (when publisher
    ["PB" publisher]))

(defn make-ris-issue-volume [issue volume]
  (concat
   (when issue
     ["IS" issue])
   (when volume
     ["VL" volume])))

(defn make-ris-serials [issns isbns]
  (concat
   (when issns
     (flatten (map #(vector "SN" %) issns)))
   (when isbns
     (flatten (map #(vector "SN" %) isbns)))))
   
(defn ->ris [metadata]
  (let [ris-pairs 
        (concat
         (make-ris-type (:type metadata))
         (make-ris-ids (:DOI metadata) (:URL metadata))
         (make-ris-titles (first (:title metadata)) 
                          (first (:container-title metadata)))
         (make-ris-authors (:author metadata))
         (make-ris-pub-date (:issued metadata))
         (make-ris-publisher (:publisher metadata))
         (make-ris-pages (:page metadata))
         (make-ris-issue-volume (:issue metadata)
                                (:volume metadata))
         (make-ris-serials (:ISSN metadata)
                           (:ISBN metadata))
         (make-ris-end))]
    (->> (map #(str (first %) "  - " (second %))
              (partition 2 ris-pairs))
         (string/join \newline))))
   
