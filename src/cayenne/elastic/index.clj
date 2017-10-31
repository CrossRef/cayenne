(ns cayenne.elastic.index
  (:require [cayenne.ids.doi :as doi-id]
            [cayenne.item-tree :as itree]
            [cayenne.util :as util]
            [clj-time.core :as t]
            [cayenne.ids.issn :as issn-id]
            [cayenne.ids.isbn :as isbn-id]
            [cayenne.ids.prefix :as prefix-id]
            [cayenne.ids.member :as member-id]
            [cayenne.ids.orcid :as orcid-id]
            [cayenne.ids :as ids]
            [clojure.string :as string]))

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

(defn journal-volume [item]
  (-> item (itree/find-item-of-subtype :journal-volume) :volume))

(defn journal-issue [item]
  (-> item (itree/find-item-of-subtype :journal-issue) :issue))

(defn item-titles [item & {:keys [subtype]}]
  (map :value (itree/get-item-rel item :title)))

(defn item-container-titles [item & {:keys [subtype]}]
  (->> (itree/get-item-rel item :ancestor)
       (mapcat #(itree/get-item-rel % :title))
       (map :value)))

(defn item-standards-body [item]
  (when-let [standards-body (-> item
                                (itree/get-tree-rel :standards-body)
                                first)]
    (select-keys standards-body [:name :acronym])))

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

(defn item-update-policy [item]
  (when-let [policy (-> item (itree/get-tree-rel :update-policy) first)]
    (:value policy)))

(defn item-domain-exclusive [item]
  (-> item
      (itree/get-item-rel :domain-exclusive)
      first
      (or false)))

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
        :orcid-authenticated (:orcid-authenticated %)
        :affiliation         (-> %
                                 (itree/get-item-rel :affiliation)
                                 (map :name)))
      (itree/get-tree-rel item contributor-rel)))
   contributions))

(defn item-funders [item]
  (map
   (fn [funder]
     (let [awards (->> (itree/get-tree-rel funder :award)
                       (map #(item-id % :awarded)))]
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
  (map
   #(-> %
        (select-keys [:name :theme :location
                      :acronym :number :sponsor])
        (assoc :start (item-date % :start))
        (assoc :end (item-date % :end)))
   (itree/get-tree-rel item :event)))

(defn item-links [item]
  (map
   #(hash-map
     :content-type    (:content-type %)         
     :url             (:value %)
     :version         (:content-version %)
     :application     (:intended-application %))
   (itree/get-tree-rel item :resource-fulltext)))

(defn item-licenses [item]
  ())

(defn item-assertions [item]
  (map
   #(select-keys % [:name :label :group-name
                    :group-label :url :explanation-url
                    :value :order])
   (itree/get-tree-rel item :assertion)))

(defn item-relations [item]
  (map
   #(hash-map
     :type        (-> % :subtype name)
     :object      (:object %)
     :object-type (:object-type %)
     :object-ns   (:object-namespace %)
     :claimed-by  (-> % :claimed-by name))
   (itree/get-tree-rel item :relation)))

(defn item-references [item]
  ())

(defn item-updated-bys [item]
  ())

(defn item-update-ofs [item]
  ())

(defn contributor-name [contributor]
  (or
   (:org-name contributor)
   (str (:give-name contributor) " " (:family-name contributor))))

(defn contributor-initials [contributor]
  (letfn [(initials [first-name]
            (when first-name
              (as-> first-name $
                (string/split $ #"[\s\-]+")
                (map first $)
                (string/join " " $))))]
    (or
     (:org-name contributor)
     (str
      (-> contributor :given-name initials)
      " "
      (:family-name contributor)))))

(defn item-base-content [item]
  (->>
   (vector
    (t/year (item-issued-date item))
    (t/year (item-date item :published-print))
    (journal-issue item)
    (journal-volume item)
    (:first-page item)
    (:last-page item))
   (concat (map :value (item-issns item)))
   (concat (map :value (item-isbns item)))
   (concat (item-titles item))
   (concat (item-container-titles item))
   (string/join " ")))

(defn item-bibliographic-content
  "Fields related to bibliographic citation look up"
  [item]
  (string/join
   " "
   (-> [(item-base-content item)]
       (concat (map contributor-initials (item-contributors item))))))

(defn item-metadata-content
  "A default set of search fields"
  [item]
  (string/join
   " "
   (-> [(item-base-content item)]
       (conj (:description item))
       (concat (map ids/extract-supplementary-id (itree/get-tree-ids item :supplementary))) ; plain supp ids
       (concat (map contributor-name (item-contributors item))) ; full names
       (concat (mapcat itree/get-item-ids (itree/get-tree-rel item :awarded))) ; grant numbers
       (concat (map :name (itree/get-tree-rel item :funder)))))) ; funder names

(defn index-command [item]
  (let [doi            (item-doi item)
        publisher      (itree/get-tree-rel item :publisher)
        journal-issue  (itree/find-item-of-subtype item :journal-issue)
        journal-volume (itree/find-item-of-subtype item :journal-volume)]
    [{:index {:_id doi}}
     {:doi              doi
      :type             (item-type item)
      :prefix           (doi-id/extract-long-prefix doi)
      :owner-prefix     (item-owner-prefix publisher)
      :member-id        (item-member-id publisher)
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

      :is-referenced-by-count (-> item (itree/get-tree-rel :cited-count) first)
      :references-count       (-> item (itree/get-tree-rel :citation) count)

      :publisher          (:name publisher)
      :publisher-location (:location publisher)

      :title                 (item-titles item :subtype :long)
      :short-title           (item-titles item :subtype :short)
      :original-title        (item-titles item :subtype :original)
      :group-title           (item-titles item :subtype :group)
      :subtitle              (item-titles item :subtype :secondary)
      :container-title       (item-container-titles item :subtype :long)
      :short-container-title (item-container-titles item :subtype :short)
      
      :first-page         (:first-page item)
      :last-page          (:last-page item)
      :issue              (:issue journal-issue)
      :volume             (:volume journal-volume)
      :description        (:description item)
      ;; :article-number
      :degree             (-> item (itree/get-item-rel :degree) (map :value))
      ;; :edition-number
      ;; :part-number
      ;; :component-number

      :update-policy      (item-update-policy item)
      :domain             (itree/get-item-rel item :domains)
      :domain-exclusive   (item-domain-exclusive item)
      :archive            (-> item (itree/get-item-rel :archived-with) (map :name))

      :abstract              (item-plain-abstract item)
      :abstract-xml          (item-xml-abstract item)
      :metadata-content      (item-metadata-content item)
      :bibliographic-content (item-bibliographic-content item)

      :isbn             (item-isbns item)
      :issn             (item-issns item)
      :link             (item-links item)
      :assertion        (item-assertions item)
      :relation         (item-relations item)
      :contributor      (item-contributors item)
      :funder           (item-funders item)
      :clinical-trial   (item-clinical-trials item)
      :event            (item-events item)
      :standards-body   (item-standards-body item)}]))

