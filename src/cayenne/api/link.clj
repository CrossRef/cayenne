(ns cayenne.api.link
  (:require [cayenne.util :as util]
            [clojure.string :as string]))

;; generate http link headers for work metadata

(def full-text-rel "http://id.crossref.org/schema/fulltext")
(def person-rel "http://id.crossref.org/schema/person")
(def license-rel "http://id.crossref.org/schema/license")

(def full-text-data-loc "http://data.crossref.org/fulltext/")

(defn make-link-header-value [uri & {:keys [rel anchor parameters] 
                                     :or {:parameters {}}}]
  (let [params (-> parameters
                   (util/?> rel assoc "rel" rel)
                   (util/?> anchor assoc "anchor" anchor))]
    (->> (concat
          [(str "<" uri ">")]
          (map (fn [[k v]] (str (name k) "=\"" v "\"")) params))
         (interpose "; ")
         (apply str))))

(defn make-full-text-link-header [doi link]
  (let [version (:content-version link)
        type (:content-type link)
        params (-> {}
                   (util/?> (and version (not= version "unspecified"))
                            assoc :version version)
                   (util/?> (and type (not= type "unspecified"))
                            assoc :type type))]
    (make-link-header-value
     (str full-text-data-loc doi)
     :rel full-text-rel
     :anchor (:URL link)
     :parameters params)))

(defn make-full-text-link-headers [metadata]
  (when (:link metadata)
    (map (partial make-full-text-link-header (:DOI metadata))
         (:link metadata))))

(defn make-person-link-headers [metadata]
  (let [persons-with-orcids 
        (filter :ORCID
                (concat
                 (:author metadata)
                 (:translator metadata)
                 (:editor metadata)
                 (:chair metadata)
                 (:contributor metadata)))]
    (map #(make-link-header-value (:ORCID %) :rel person-rel)
         persons-with-orcids)))

(defn make-license-link-header [license]
  (let [version (:content-version license)
        params (if (and version (not= version "unspecified"))
                 {:version version} {})]
    (make-link-header-value (:URL license) :rel license-rel :parameters params)))

(defn make-license-link-headers [metadata]
  (when (:license metadata)
    (map make-license-link-header (:license metadata))))

(defn make-link-headers [metadata]
  (string/join 
   ", "
   (concat
    (make-full-text-link-headers metadata)
    (make-license-link-headers metadata)
    (make-person-link-headers metadata))))
     
            
