(ns cayenne.elastic.index
  (:require [cayenne.ids.doi :as doi-id]
            [cayenne.item-tree :as itree]
            [cayenne.util :as util]))

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

(defn item-type [item]
  (-> item itree/get-item-subtype item name))

(defn item-issued-date [item]
  (->> (concat
        (get-item-rel item :posted)
        (get-item-rel item :published-print)
        (get-item-rel item :published-online)
        (get-item-rel item :published-other)
        (get-item-rel item :published)
        (get-tree-rel item :content-created))
       (sort-by particle->date-time)
       first
       particle->date-time))

(defn item-date [item date-rel]
  (-> item (get-item-rel date-rel) first particle->date-time))

(defn item-plain-abstract [item]
  (-> item (get-item-rel :abstract) first :plain))

(defn item-xml-abstract [item]
  (-> item (get-item-rel :abstract) first :xml))

(defn item-standards-body [item]
  ())

(defn item-issns [item]
  ())

(defn item-isbns [item]
  ())

(defn item-contributors [item]
  ())

(defn item-funders [item]
  (let [funders (get-tree-rel item :funder)]
    (map
     (fn [funder]
       (let [awards (->> (get-tree-rel funder :award)
                         (map #(item-id % :award)))]
         {:name            (:name funder)
          :doi             (item-id funder :long-doi)
          :doi-asserted-by (:doi-asserted-by funder)
          :award           awards})))))

(defn item-clinical-trials [item]
  (let [trials (get-tree-rel item :clinical-trial-number)]
    (map
     #(hash-map
       :number   (:ctn %)
       :registry (:registry %)
       :type     (:ctn-type %)))))

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
  (let [doi (item-id item :long-doi :converter doi-id/normalize-long-doi)]
    [{:index {:_id doi}}
     {:doi              doi
      :type             (item-type item)
      :prefix           (doi-id/extract-long-prefix doi)
      :owner-prefix     (item-id item :owner-prefix)
      :member-id        (item-id item :member-id)
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

      :abstract         (item-plain-abstract item)
      :abstract-xml     (item-xml-abstract item)

      :funder           (item-funders item)
      :clinical-trial   (item-clinical-trials item)
      }]))

