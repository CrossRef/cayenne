(ns cayenne.api.v1.filter
  (:require [clj-time.core :as dt]
            [clj-time.coerce :as dc]
            [clj-time.predicates :as dp]
            [clojure.string :as string]
            [cayenne.util :as util]
            [cayenne.conf :as conf]
            [cayenne.ids :as ids]
            [cayenne.ids.type :as type-id]
            [cayenne.ids.prefix :as prefix]
            [cayenne.ids.issn :as issn]
            [cayenne.ids.isbn :as isbn]
            [cayenne.ids.orcid :as orcid]
            [cayenne.ids.doi :as doi-id]
            [cayenne.ids.ctn :as ctn]
            [clojure.string :as string]
            [cayenne.ids.member :as member-id]))

;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn split-date [date-str]
  (let [date-parts (string/split date-str #"-")]
    {:year (Integer/parseInt (first date-parts))
     :month (Integer/parseInt (nth date-parts 1 "-1"))
     :day (Integer/parseInt (nth date-parts 2 "-1"))}))

(defn obj-date [date-str & {:keys [direction]}]
  (let [date-parts (split-date date-str)
        d (cond (not= (:day date-parts) -1)
                (dt/date-time (:year date-parts) (:month date-parts) (:day date-parts))
                (not= (:month date-parts) -1)
                (dt/date-time (:year date-parts) (:month date-parts))
                :else
                (dt/date-time (:year date-parts)))]
    (if (not= direction :until)
      d
      (cond (not= (:day date-parts) -1)
            ;; end of day
            (dt/plus d (dt/days 1))
            (not= (:month date-parts) -1)
            ;; end of month
            (dt/plus d (dt/months 1))
            :else
            ;; end of year
            (dt/plus d (dt/years 1))))))

;; Elastic filters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn greater-than-zero [field]
  (fn [val]
    (cond (#{"t" "true" "1"} (.toLowerCase val))
          {:occurrence :filter
           :clause {:range {field {:gte 1}}}}
          (#{"f" "false" "0"} (.toLowerCase val))
          {:occurrence :filter
           :clause {:range {field {:lte 0}}}})))

(defn existence [field]
  (fn [val]
    (cond (#{"t" "true" "1"} (.toLowerCase val))
          {:occurrence :filter
           :clause {:exists {:field field}}}
          (#{"f" "false" "0"} (.toLowerCase val))
          {:occurrence :must-not
           :clause {:exists {:field field}}})))

(defn bool [field]
  (fn [val]
    (cond (#{"t" "true" "1"} (.toLowerCase val))
          {:occurrence :filter
           :clause {:term {field true}}}
          (#{"f" "false" "0"} (.toLowerCase val))
          {:occurrence :filter
           :clause {:term {field false}}})))

(defn equality [field & {:keys [transformer] :or {transformer identity}}]
  (fn [val] {:occurrence :filter :clause {:term {field (transformer val)}}}))

(defn date-range [field end-point]
  (fn [val]
    (condp = end-point
      :from
      {:occurrence :filter
       :clause {:range {field {:gte (.toString (obj-date val :direction end-point))}}}}
      :until
      {:occurrence :filter
       :clause {:range {field {:lte (.toString (obj-date val :direction end-point))}}}})))

(defn replace-keys [m kr]
  (into {} (map (fn [[k v]] (if-let [replacement (get kr k)] [replacement v] [k v])) m)))

;; todo handle multiple compound fields (only getting one at a time right now)
;; todo :transformers
(defn nested-terms [prefix suffixes & {:keys [transformers]}]
  (fn [val]
    (println val)
    (let [field-name (->> val first (str (name prefix) ".") keyword)]
      {:occurrence :filter
       :clause
       {:nested {:path prefix :query {:term {field-name (-> val second first)}}}}})))

  ;; (letfn [(field-name [field]
  ;;           (keyword (str (name prefix)
  ;;                         "."
  ;;                         (-> (keyword field) suffixes name))))]
  ;;   (fn [m]
  ;;     (println m)
  ;;     (let [term-filters (map #(hash-map :terms {(field-name (first %))
  ;;                                                (-> % second first)})
  ;;                             m)]
  ;;       {:nested {:path prefix :query {:bool {:filter term-filters}}}}))))

(defn nested [filter-fn path]
  (fn [val]
    (let [filter-clause (filter-fn val)]
      {:occurrence (:occurrence filter-clause)
       :clause {:nested {:path path :query {:bool {:filter (:clause filter-clause)}}}}})))

;; Filter definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def std-filters
  {"from-update-date"          (date-range :deposited :from)
   "until-update-date"         (date-range :deposited :until)
   "from-index-date"           (date-range :indexed :from)
   "until-index-date"          (date-range :indexed :until)
   "from-deposit-date"         (date-range :deposited :from)
   "until-deposit-date"        (date-range :deposited :until)
   "from-created-date"         (date-range :first-deposited :from)
   "until-created-date"        (date-range :first-deposited :until)
   "from-pub-date"             (date-range :published :from)
   "until-pub-date"            (date-range :published :until)
   "from-issued-date"          (date-range :published :from)
   "until-issued-date"         (date-range :published :until)
   "from-online-pub-date"      (date-range :published-online :from)
   "until-online-pub-date"     (date-range :published-online :until)
   "from-print-pub-date"       (date-range :published-print :from)
   "until-print-pub-date"      (date-range :published-print :until)
   "from-posted-date"          (date-range :posted :from)
   "until-posted-date"         (date-range :posted :until)
   "from-accepted-date"        (date-range :accepted :from)
   "until-accepted-date"       (date-range :accepted :until)
   "from-event-start-date"     (date-range :event.start :from)
   "until-event-start-date"    (date-range :event.start :until)
   "from-event-end-date"       (date-range :event.end :from)
   "until-event-end-date"      (date-range :event.end :until)
   "from-approved-date"        (date-range :approved :from)
   "until-approved-date"       (date-range :approved :until)
   "has-event"                 (existence :event)
   "is-update"                 (-> (existence :update-to) (nested :update-to))
   "has-update"                (-> (existence :updated-by) (nested :updated-by))
   "content-domain"            (equality :domain)
   "has-content-domain"        (existence :domain)
   "has-domain-restriction"    (bool :domain-exclusive)
   "updates"                   (-> (equality :update.doi
                                             :transformer doi-id/extract-long-doi)
                                   (nested :update))
   "update-type"               (-> (equality :update.type)
                                   (nested :update))
   "has-abstract"              (existence :abstract)
   "has-full-text"             (-> (existence :link) (nested :link))
   "has-license"               (-> (existence :license) (nested :license))
   "has-references"            (greater-than-zero :references-count)
   "has-update-policy"         (existence :update-policy)
   "has-archive"               (existence :archive)
   "has-orcid"                 (-> (existence :contributor.orcid) (nested :contributor))
   "has-authenticated-orcid"   (-> (bool :contributor.orcid-authenticated)
                                   (nested :contributor))
   "has-affiliation"           (-> (existence :contributor.affiliation)
                                   (nested :contributor))
   "has-funder"                (-> (existence :funder) (nested :funder))
   "has-funder-doi"            (-> (existence :funder.doi) (nested :funder))
   "has-award"                 (-> (existence :funder.award) (nested :funder))
   "has-relation"              (-> (existence :relation) (nested :relation))
   "funder-doi-asserted-by"    (-> (equality :funder.doi-asserted-by) (nested :funder))
   "has-assertion"             (-> (existence :assertion) (nested :assertion))
   "has-clinical-trial-number" (-> (existence :clinical-trial) (nested :clinical-trial))
   "full-text"                 (nested-terms :link {:type :content-type
                                                    :application :application
                                                    :version :version})
   "license"                   (nested-terms :license {:url :url
                                                       :version :version
                                                       :delay :delay}
                                             :matchers {:delay #(str ":[* TO " % "]")})
   "archive"                   (equality :archive)
   "article-number"            (equality :article-number)
   "issn"                      (equality :issn.value :transformer issn/extract-issn)
   "isbn"                      (equality :isbn.value :transformer isbn/extract-isbn)
   "type"                      (equality :type)
   "type-name"                 (equality :type)
   "orcid"                     (-> (equality :contributor.orcid
                                             :transformer orcid/extract-orcid)
                                   (nested :contriburor))
   "assertion"                 (-> (equality :assertion.name) (nested :assertion))
   "assertion-group"           (-> (equality :assertion.group-name) (nested :assertion))
   "doi"                       (equality :doi :transformer doi-id/extract-long-doi)
   "group-title"               (equality :group-title)
   "container-title"           (equality :container-title)
   "clinical-trial-number"     (-> (equality :clinical-trial.number)
                                   (nested :clinical-trial))
   "alternative-id"            (equality :supplementary-id)
   "award"                     (nested-terms :funder {:funder-doi :doi
                                                      :funder     :doi
                                                      :number     :award}
                                       :transformers
                                       {:funder-doi doi-id/with-funder-prefix
                                        :funder doi-id/with-funder-prefix
                                        :number #(-> %
                                                     string/lower-case
                                                     string/replace #"[\s_\-]+" "")})
   "relation"                  (nested-terms :relation {:type :type
                                                        :object-type :object-type
                                                        :object-ns :object-ns
                                                        :claimed-by :claimed-by
                                                        :object :object})
   "member"                    (equality :member-id
                                         :transformer member-id/extract-member-id)
   "journal"                   (equality :journal-id)
   "prefix"                    (equality :owner-prefix
                                         :transformer prefix/extract-prefix)
   "funder"                    (equality :funder-doi
                                         :transformer doi-id/with-funder-prefix)})
                              
(def member-filters
  {"prefix"                (equality :prefix.value)
   "has-public-references" (bool :prefix.public-references)})
   
(def funder-filters
  {"location"   (equality :country)
   "child"      (equality :child :transformer doi-id/with-funder-prefix)
   "ancestor"   (equality :ancestor :transformer doi-id/with-funder-prefix)
   "parent"     (equality :parent :transformer doi-id/with-funder-prefix)
   "descendant" (equality :descendant :transformer doi-id/with-funder-prefix)})
