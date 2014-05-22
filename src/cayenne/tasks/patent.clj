(ns cayenne.tasks.patent
  (:require [somnium.congomongo :as m]
            [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi-id]
            [clojure.data.csv :as csv]
            [clojure.string :as string]))

(defn load-citation-csv [input & {:keys [consume separator batch-id] 
                                  :or {consume true separator \, batch-id nil}}]
  (doseq [citation-line (csv/read-csv input :separator separator)]
    (m/with-mongo (conf/get-service :mongo)
      (let [patent-key (-> citation-line first string/upper-case string/trim)
            doi (-> citation-line second doi-id/normalize-long-doi)
            language (-> citation-line (nth 2) string/lower-case string/trim)
            patent-title (-> citation-line (nth 3) string/trim)]
        (when consume
          (when-not (m/fetch-one "citations" :where {"from.id" patent-key
                                                     "to.id" doi})
            (m/insert! "citations"
                       {:from {:type :patent :id patent-key :authority :cambia}
                        :to {:type :doi :id doi :authority :crossref}
                        :batch-id batch-id}))
          (when-not (m/fetch-one "patents" :where {"patent_key" patent-key})
            (m/insert! "patents"
                       {:patent_key patent-key
                        :pub_key patent-key
                        :lang language
                        :title patent-title})))))))
                        


