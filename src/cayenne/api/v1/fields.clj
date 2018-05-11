(ns cayenne.api.v1.fields)

(def work-fields
  {"bibliographic"          :bibliographic-content-text
   "title"                  :title-text
   "container-title"        :container-title-text
   "event-name"             :event.name
   "event-theme"            :event.theme
   "event-location"         :event.location
   "event-sponsor"          :event.sponsor
   "event-acronym"          :event.acronym
   "standards-body-name"    :standards-body.name
   "standards-body-acronym" :standards-body.acronym
   "degree"                 :degree-text
   "affiliation"            :affiliation-text
   "publisher-name"         :publisher-text
   "publisher-location"     :publisher-location-text
   "funder-name"            :funder-name-text
   "author"                 :author-text
   "editor"                 :editor-text
   "chair"                  :chair-text
   "translator"             :translator-text
   "contributor"            :contributor-text})

(defn with-field-queries [es-body {:keys [field-terms]}]
  (if (not-empty field-terms)
    (update-in
     es-body
     [:query :match]
     (fn [matches]
       (merge
        matches
        (apply
         merge
         (map (fn [t]
                {(-> t first work-fields)
                 (-> t second)})
              field-terms)))))
    es-body))
