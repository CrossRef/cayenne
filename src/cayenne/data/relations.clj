(ns cayenne.data.relations
  (:require [clojure.set :as set]))

(def inter-relations
  [:is-derived-from
   :has-derivation
   :is-review-of
   :has-review
   :is-comment-on
   :has-comment
   :is-reply-to
   :has-reply
   :based-on-data
   :is-data-basis-for
   :has-related-material
   :is-related-material
   :is-compiled-by
   :compiles
   :is-documented-by
   :documents
   :is-supplement-to
   :is-supplemented-by
   :is-continued-by
   :continues
   :is-part-of
   :has-part
   :references
   :is-referenced-by
   :is-based-on
   :is-basis-for
   :requires
   :is-required-by])

(def intra-relations
  [:has-translation
   :is-translation-of
   :has-preprint
   :is-preprint-of
   :has-manuscript
   :is-manuscript-of
   :has-expression
   :is-expression-of
   :has-manifestation
   :is-manifestation-of
   :replaces
   :is-replaced-by
   :is-same-as
   :is-identical-to
   :is-original-form-of
   :is-varient-form-of
   :has-version
   :is-version-of
   :has-format])

(def relations (concat inter-relations intra-relations))

(def relation-antonym
  (let [antonyms {:is-derived-from      :has-derivation
                  :is-review-of         :has-review
                  :is-comment-on        :has-comment
                  :is-reply-to          :has-reply
                  :based-on-data        :is-data-basis-for
                  :has-related-material :is-related-material
                  :is-compiled-by       :compiles
                  :is-documented-by     :documents
                  :is-supplement-to     :is-supplemented-by
                  :is-continued-by      :continues
                  :is-part-of           :has-part
                  :references           :is-referenced-by
                  :is-based-on          :is-basis-for
                  :requires             :is-required-by

                  :has-translation      :is-translation-of
                  :has-preprint         :is-preprint-of
                  :has-manuscript       :is-manuscript-of
                  :has-expression       :is-expression-of
                  :has-manifestation    :is-manifestation-of
                  :replaces             :is-replaced-by
                  :has-version          :is-version-of

                  :is-same-as           :is-same-as
                  :is-identical-to      :is-identical-to}]
    (into antonyms (map #(vector (second %) (first %)) antonyms))))

;; defines relations that don't have a recipricol representation in
;; crossref's relations.xsd schema
(def unworkable-relations
  (set/difference (set relations) (set (keys relation-antonym))))

  
