(ns cayenne.ids.update-type
  (:require [clojure.string :as str]))

(def update-type-dictionary
  {:addendum "Addendum"
   :clarification "Clarification"
   :correction  "Correction"
   :corrigendum "Corrigendum"
   :erratum "Erratum"
   :expression_of_concern "Expression of concern"
   :new_edition "New edition"
   :new_version "New version"
   :partial_retraction "Partial retraction"
   :removal "Removal"
   :retraction "Retraction"
   :withdrawal "Withdrawal"

   ;; Not valid but commonly used
   :err "Erratum"})

(defn update-label [update-type-id]
  (-> update-type-id name str/lower-case keyword update-type-dictionary))
