(ns cayenne.formats.citation
  (:import [de.undercouch.citeproc.csl 
            CSLItemDataBuilder CSLDateBuilder CSLNameBuilder 
            CSLName CSLItemData CSLType]
           [de.undercouch.citeproc CSL ListItemDataProvider]))

(defn make-csl-issued-date [metadata]
  (let [year (get-in metadata [:issued :date-parts 0 0])
        month (get-in metadata [:issued :date-parts 0 1])
        day (get-in metadata [:issued :date-parts 0 2])
        builder (CSLDateBuilder.)]
    (cond
     (and year month day)
     (.dateParts builder year month day)
     (and year month)
     (.dateParts builder year month)
     year
     (.dateParts builder year))
    (.build builder)))

(defn make-csl-contributor [contributor]
  (-> (CSLNameBuilder.)
      (.family (:family contributor))
      (.given (:given contributor))
      (.build)))

(defn ->csl-item [metadata]
  (let [builder (CSLItemDataBuilder.)]
    (-> builder
        (.id "1")
        (.type CSLType/ARTICLE_JOURNAL)
        (.source (:source metadata))
        (.DOI (:DOI metadata))
        (.URL (:URL metadata))
        (.publisher (:publisher metadata))
        (.issued (make-csl-issued-date metadata))
        (.volume (:volume metadata))
        (.issue (:issue metadata))
        (.page (:page metadata))
        (.author (into-array CSLName (map make-csl-contributor (:author metadata))))
        (.translator (into-array CSLName (map make-csl-contributor (:translator metadata))))
        (.editor (into-array CSLName (map make-csl-contributor (:editor metadata)))))
    (doseq [isbn (:ISBN metadata)] (.ISBN builder isbn))
    (doseq [issn (:ISSN metadata)] (.ISSN builder issn))
    (doseq [title (:title metadata)] (.title builder title))
    (doseq [container-title (:container-title metadata)] (.containerTitle builder container-title))
    (doseq [archive (:archive metadata)] (.archive builder archive))
    (.build builder)))

(defn ->citation [metadata & {:keys [style language format]
                              :or {style "apa"
                                   language "en-US"
                                   format "text"}}]
  (let [item-data (->csl-item metadata)
        item-provider (ListItemDataProvider.
                       (into-array CSLItemData [item-data]))
        csl (doto (CSL. item-provider style language)
              (.setOutputFormat format)
              (.registerCitationItems (into-array String [(.getId item-data)])))]
    (-> csl (.makeBibliography) (.makeString))))
