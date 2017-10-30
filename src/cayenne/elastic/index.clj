(ns cayenne.elastic.index
  (:require [cayenne.ids.doi :as doi-id]
            [cayenne.item-tree :as itree]
            [cayenne.util :as util]
            [clj-time.core :as t]
            [cayenne.ids.issn :as issn-id]
            [cayenne.ids.isbn :as isbn-id]
            [cayenne.ids.prefix :as prefix-id]
            [cayenne.ids.member :as member-id]
            [cayenne.ids.orcid :as orcid-id]))

(def contributions [:author :chair :editor :translator :contributor])

(defn particle->date-time [particle]
  (let [year (-> particle :year util/parse-int-safe)
        month (-> particle :month util/parse-int-safe)
        day (-> particle :day util/parse-int-safe)]
    (cond (and year month day)
          (if (< (t/number-of-days-in-the-month year month) day)
            (t/date-time year month)
            (t/date-time year month day))
          (and year month)
          (t/date-time year month)
          :else
          (t/date-time year))))

(defn item-id [item id-type & {:keys [converter]
                               :or {converter identity}}]
  (when-let [id (-> item (itree/get-item-ids id-type) first)]
    (converter id)))

(defn item-doi [item]
  (item-id item :long-doi :converter doi-id/extract-long-doi))

(defn item-issn [item]
  (item-id item :issn :converter issn-id/extract-issn))

(defn item-isbn [item]
  (item-id item :isbn :converter isbn-id/extract-isbn))

(defn item-owner-prefix [item]
  (item-id item :owner-prefix :converter prefix-id/extract-prefix))

(defn item-member-id [item]
  (item-id item :member-id :converter member-id/extract-member-id))

(defn item-orcid [item]
  (item-id item :orcid :converter orcid-id/extract-orcid))

(defn item-type [item]
  (-> item itree/get-item-subtype name))

(defn item-issued-date [item]
  (->> (concat
        (itree/get-item-rel item :posted)
        (itree/get-item-rel item :published-print)
        (itree/get-item-rel item :published-online)
        (itree/get-item-rel item :published-other)
        (itree/get-item-rel item :published)
        (itree/get-tree-rel item :content-created))
       (sort-by particle->date-time)
       first
       particle->date-time))

(defn item-date [item date-rel]
  (when-let [first-date (-> item (itree/get-item-rel date-rel) first)]
    (particle->date-time first-date)))

(defn item-plain-abstract [item]
  (-> item (itree/get-item-rel :abstract) first :plain))

(defn item-xml-abstract [item]
  (-> item (itree/get-item-rel :abstract) first :xml))

(defn item-standards-body [item]
  ())

(defn item-issns [item]
  (map
   #(hash-map :value (-> % :value issn-id/extract-issn)
              :type  (:kind %))
   (itree/get-tree-rel item :issn)))

(defn item-isbns [item]
  (map
   #(hash-map :value (-> % :value isbn-id/extract-isbn)
              :type  (:kind %))
   (itree/get-tree-rel item :isbn)))

(defn item-contributors [item]
  (mapcat
   (fn [contributor-rel]
     (map
      #(hash-map
        :contribution        (name contributor-rel)
        :given-name          (:first-name %)
        :family-name         (:last-name %)
        :org-name            (:name %)
        :suffix              (:suffix %)
        :prefix              (:prefix %)
        :orcid               (item-orcid %)
        :orcid-authenticated (:orcid-authenticated %))
      (itree/get-tree-rel item contributor-rel)))
   contributions))

(defn item-funders [item]
  (map
   (fn [funder]
     (let [awards (->> (itree/get-tree-rel funder :award)
                       (map #(item-id % :award)))]
       {:name            (:name funder)
        :doi             (item-doi funder)
        :doi-asserted-by (:doi-asserted-by funder)
        :award           awards}))
   (itree/get-tree-rel item :funder)))

(defn item-clinical-trials [item]
  (map
   #(hash-map
     :number   (:ctn %)
     :registry (:registry %)
     :type     (:ctn-type %))
   (itree/get-tree-rel item :clinical-trial-number)))

(defn item-events [item]
  ())

(defn item-links [item]
  ())

(defn item-licenses [item]
  ())

(defn item-assertions [item]
  ())

(defn item-relations [item]
  ())

(defn item-references [item]
  ())

(defn item-updated-bys [item]
  ())

(defn item-update-ofs [item]
  ())

(defn index-command [item]
  (let [doi (item-doi item)]
    [{:index {:_id doi}}
     {:doi              doi
      :type             (item-type item)
      :prefix           (doi-id/extract-long-prefix doi)
      :owner-prefix     (item-owner-prefix item)
      :member-id        (item-member-id item)
      :supplementary-id (itree/get-item-ids item :supplementary)
      
      :published        (item-issued-date item)
      :published-online (item-date item :published-online)
      :published-print  (item-date item :published-print)
      :published-other  (item-date item :published-other)
      :posted           (item-date item :posted)
      :accepted         (item-date item :accepted)
      :content-created  (item-date item :content-created)
      :content-updated  (item-date item :content-updated)
      :approved         (item-date item :approved)
      :deposited        (item-date item :deposited)
      :first-deposited  (item-date item :first-deposited)

      ;; titles

      ;; article / journal stuff

      :abstract         (item-plain-abstract item)
      :abstract-xml     (item-xml-abstract item)

      :isbn             (item-isbns item)
      :issn             (item-issns item)
      :contributor      (item-contributors item)
      :funder           (item-funders item)
      :clinical-trial   (item-clinical-trials item)
      }]))

