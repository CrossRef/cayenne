(ns cayenne.api.transform
  (:require [cayenne.util :as util]
            [cayenne.conf :as conf]
            [cayenne.formats.rdf :as rdf]
            [cayenne.formats.ris :as ris]
            [cayenne.formats.citation :as citation]
            [clj-http.client :as http]))

(defmulti ->format :media-type)

(defmethod ->format "text/turtle" [representation metadata]
  (rdf/->turtle metadata))

(defmethod ->format "text/n3" [representation metadata]
  (rdf/->n3 metadata))

(defmethod ->format "text/n-triples" [representation metadata]
  (rdf/->n-triples metadata))

(defmethod ->format "text/n3" [representation metadata]
  (rdf/->n3 metadata))

(defmethod ->format "application/rdf+xml" [representation metadata]
  (rdf/->xml metadata))

(defmethod ->format "application/vnd.citationstyles.csl+json" [representation metadata]
  metadata)

(defmethod ->format "application/x-research-info-systems" [representation metadata]
  (ris/->ris metadata))

(defmethod ->format "text/x-bibliography" [representation metadata]
  (let [args (concat
              (when-let [style (get-in representation [:parameters :style])]
                [:style style])
              (when-let [lang (get-in representation [:parameters :locale])]
                [:language lang])
              (when-let [format (get-in representation [:parameters :format])]
                [:format format]))]
    (apply citation/->citation metadata args)))

(defmethod ->format "application/x-bibtex" [representation metadata]
  (citation/->citation metadata :style "bibtex"))

;; for now we retrieve original unixref and unixsd, but in future perhaps we
;; will generate from citeproc

(defmethod ->format "application/vnd.crossref.unixref+xml" [representation metadata]
  (-> (str (conf/get-param [:upstream :unixref-url]) (:DOI metadata))
      (http/get {:connection-manager (conf/get-service :conn-mgr)
                 :throw-exceptions false})
      (:body)))

(defmethod ->format "application/vnd.crossref.unixsd+xml" [representation metadata]
  (-> (str (conf/get-param [:upstream :unixsd-url]) (:DOI metadata))
      (http/get {:connection-manager (conf/get-service :conn-mgr)
                 :throw-exceptions false})
      (:body)))
