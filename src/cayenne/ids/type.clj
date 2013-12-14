(ns cayenne.ids.type)

(def type-dictionary {:journal-article {:index-id "Journal Article" 
                                        :label "Journal Article"}
                      :journal-issue {:index-id "Journal Issue"
                                      :label "Journal Issue"}
                      :journal-volume {:index-id "Journal Volume" 
                                       :label "Journal Volume"}
                      :journal {:index-id "Journal"
                                :label "Journal"}
                      :proceedings-article {:index-id "Proceedings Article" 
                                            :label "Proceedings Article"}
                      :dataset {:index-id "Dataset"
                                :label "Dataset"}
                      :report {:index-id "Report"
                               :label "Report"}
                      :report-series {:index-id "Report Series"
                                      :label "Report Series"}
                      :standard {:index-id "Standard"
                                 :label "Standard"}
                      :standard-series {:index-id "Standard Series"
                                        :label "Standard Series"}
                      :edited-book {:index-id "Edited Book"
                                    :label "Edited Book"}
                      :monograph {:index-id "Monograph"
                                  :label "Monograph"}
                      :reference-book {:index-id "Reference Book"
                                       :label "Reference Book"}
                      :book {:index-id "Book"
                             :label "Book"}
                      :book-series {:index-id "Book Series"
                                    :label "Book Series"}
                      :book-set {:index-id "Book Set"
                                 :label "Book Set"}
                      :book-chapter {:index-id "Chapter"
                                     :label "Book Chapter"}
                      :book-section {:index-id "Section"
                                     :label "Book Section"}
                      :book-part {:index-id "Part"
                                  :label "Book Part"}
                      :book-track {:index-id "Track"
                                   :label "Book Track"}
                      :book-entry {:index-id "Reference Entry"
                                   :label "Book Entry"}
                      :dissertation {:index-id "Dissertation"
                                     :label "Dissertation"}
                      :other {:index-id "Other"
                              :label "Other"}})

(defn ->index-id [id]
  (when-let [t (get type-dictionary (keyword id))]
    (:index-id t)))
