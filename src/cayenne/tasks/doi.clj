(ns cayenne.tasks.doi
  (:require [cayenne.conf :as conf]
            [cayenne.ids.doi :as doi-id]
            [somnium.congomongo :as m]))

;; calculate stats about DOIs

; rfc 3986
(def url-reserved #{\: \/ \? \# \[ \] \@ \! \$ \& \' \( \) \* \+ \, \; \=})

(def doi-stats (agent {:count 0
                       :max-string ""
                       :min-string "http://dx.doi.org/10.55555/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                       :max-length Integer/MIN_VALUE
                       :min-length Integer/MAX_VALUE
                       :count-reserved 0
                       :total-length 0}))
; :count-with-url-char
; :prefix max min avg len

(defn update-stats [stats doi]
  (let [doi-length (count doi)
        doi-suffix (doi-id/extract-long-suffix doi)]
    (-> stats
        (update-in [:count-reserved] #(if (some url-reserved doi-suffix) (inc %) %))
        (update-in [:histogram doi-length] #(if % (inc %) 1))
        (update-in [:max-string] #(if (> doi-length (count %))
                                    doi
                                    (:max-string stats)))
        (update-in [:min-string] #(if (< doi-length (count %))
                                    doi
                                    (:min-string stats)))
        (update-in [:count] inc)
        (update-in [:total-length] (partial + doi-length))
        (update-in [:max-length] (partial max doi-length))
        (update-in [:min-length] (partial min doi-length)))))

(defn aggregate-stats [stats]
  (-> stats
      (assoc-in [:avg-length] (/ (:total-length stats)
                                  (:count stats)))))

(defn update-stats-with-doc [stats doc]
  (->> doc (:id) (first) (update-stats stats)))

(defn run-stats [doi-collection]
  (m/with-mongo (conf/get-service :mongo)
    (doseq [doc (m/fetch doi-collection :only [:doi "published.year"])]
      (send doi-stats update-stats-with-doc doc)))
  (send doi-stats aggregate-stats))

