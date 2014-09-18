(ns cayenne.tasks.datomic
  (:require [cayenne.conf :as conf]
            [cayenne.item-tree :as t]
            [datomic.api :as d]
            [clojure.string :as string]))

(def relation-types
  [:isCitedBy
   :cites
   :isSupplementTo
   :isSupplementedBy
   :isContinuedBy
   :continues
   :isUpdateTo
   :isUpdatedBy
   :isUpdatePolicyOf
   :hasUpdatePolicy
   :isNewVersionOf
   :isPreviousVersionOf
   :isPartOf
   :hasPart
   :isReferencedBy
   :references
   :isDocumentedBy
   :documents
   :isCompiledBy
   :compiles
   :isVariantFormOf
   :isOriginalFormOf
   :isFundedBy
   :funds
   :isCreatedBy
   :created
   :isEditedBy
   :edited
   :sameAs])

(def relation-one-way-antonyms
  {:isCitedBy :cites
   :isSupplementTo :isSupplementedBy
   :isContinuedBy :continues
   :isUpdateTo :isUpdatedBy
   :isUpdatePolicyOf :hasUpdatePolicy
   :isNewVersionOf :isPreviousVersionOf
   :isPartOf :hasPart
   :isReferencedBy :references
   :isDocumentedBy :documents
   :isCompiledBy :compiles
   :isVariantFormOf :isOriginalFormOf
   :isFundedBy :funds
   :isCreatedBy :created
   :isEditedBy :edited
   :sameAs :sameAs})

(def relation-antonyms
  (let [one-way relation-one-way-antonyms
        t-other (into {} (map vector (vals one-way) (keys one-way)))]
    (merge one-way t-other)))

(def relation-synonyms
  {:isIdenticalTo :sameAs})

(def urn-schema
  [{:db/id #db/id[:db.part/db]
    :db/ident :urn/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many
    :db/index true
    :db/fulltext true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :urn/value
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity ; inserts for an existing URN value will merge related attributes
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :urn/type
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/index true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :urn/entityType
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/index true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :urn/availableFrom
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/index true
    :db.install/_attribute :db.part/db}
   {:db/id #db/id[:db.part/db]
    :db/ident :urn/source
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/index true
    :db.install/_attribute :db.part/db}

   ;; enum types
   {:db/id #db/id[:db.part/user]
    :db/ident :urn.source/crossref}
   {:db/id #db/id[:db.part/user]
    :db/ident :urn.source/datacite}
   
   {:db/id #db/id[:db.part/user]
    :db/ident :urn.type/orcid}
   {:db/id #db/id[:db.part/user]
    :db/ident :urn.type/doi}
   {:db/id #db/id[:db.part/user]
    :db/ident :urn.type/issn}

   {:db/id #db/id[:db.part/user]
    :db/ident :urn.entityType/person}
   {:db/id #db/id[:db.part/user]
    :db/ident :urn.entityType/org}
   {:db/id #db/id[:db.part/user]
    :db/ident :urn.entityType/work}
   {:db/id #db/id[:db.part/user]
    :db/ident :urn.entityType/journal}
   {:db/id #db/id[:db.part/user]
    :db/ident :urn.entityType/updatePolicy}])

(def relations-schema
  (map
   #(hash-map
     :db/id (d/tempid :db.part/db)
     :db/ident %
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/many
     :db/index true
     :db.install/_attribute :db.part/db)
   relation-types))

(defn ->rel [untidy-rel-name]
  (let [rel (-> (str
                 (string/lower-case (apply str (take 1 untidy-rel-name)))
                 (apply str (drop 1 untidy-rel-name)))
                keyword)]
      (or (relation-synonyms rel) rel)))
    
(defn person-name [person]
  (cond 
   (:name person)
   (:name person)
   (and (:first-name person) (:last-name person))
   (str (:first-name person) " " (:last-name person))
   :else
   (:last-name person)))

(defn recreate-db! []
  (let [uri (conf/get-param [:service :datomic :url])]
    (d/delete-database uri)
    (d/create-database uri)
    (let [conn (d/connect uri)]
      (d/transact conn (concat urn-schema relations-schema))
      (conf/set-service! :datomic conn))))

(defn connect! []
  (conf/set-service! :datomic (d/connect (conf/get-param [:service :datomic :url]))))

(defn funder->urn-datums [work-tempid funder]
  (when-let [funder-doi (-> funder (t/get-item-ids :long-doi) first)]
    (let [funder-tempid (d/tempid :db.part/user)]
      [{:db/id funder-tempid
        :urn/type :urn.type/doi
        :urn/entityType :urn.entityType/org
        :urn/name (:name funder)
        :urn/value funder-doi
        :funds work-tempid}
       {:db/id work-tempid
        :isFundedBy funder-tempid}])))

(defn author->urn-datums [work-tempid author]
  (when-let [orcid (-> author (t/get-item-ids :orcid) first)]
    (let [author-tempid (d/tempid :db.part/user)]
      [{:db/id author-tempid
        :urn/type :urn.type/orcid
        :urn/entityType :urn.entityType/person
        :urn/name (person-name author)
        :urn/value orcid
        :created work-tempid}
       {:db/id work-tempid
        :isCreatedBy author-tempid}])))

(defn editor->urn-datums [work-tempid editor]
  (when-let [orcid (-> editor (t/get-item-ids :orcid) first)]
    (let [editor-tempid (d/tempid :db.part/user)]
      [{:db/id editor-tempid
        :urn/type :urn.type/orcid
        :urn/entityType :urn.entityType/person
        :urn/name (person-name editor)
        :urn/value orcid
        :edited work-tempid}
       {:db/id work-tempid
        :isEditedBy editor-tempid}])))

(defn update->urn-datums [work-tempid update]
  (let [updatee-tempid (d/tempid :db.part/user)]
    [{:db/id updatee-tempid
      :urn/type :urn.type/doi
      :urn/entityType :urn.entityType/work
      :urn/value (-> update :value)
      :isUpdatedBy work-tempid}
     {:db/id work-tempid
      :isUpdateTo updatee-tempid}]))

(defn update-policy->urn-datums [work-tempid update-policy]
  (let [update-policy-tempid (d/tempid :db.part/user)]
    [{:db/id update-policy-tempid
      :urn/type :urn.type/doi
      :urn/entityType :urn.entityType/updatePolicy
      :urn/value (-> update-policy :value)
      :isUpdatePolicyOf work-tempid}
     {:db/id work-tempid
      :hasUpdatePolicy update-policy-tempid}]))

(defn journal->urn-datums [work-tempid journal]
  (let [issns (t/get-item-ids journal :issn)
        journal-tempids (take (count issns) 
                              (repeatedly #(d/tempid :db.part/user)))]
    (concat
     (map
      #(hash-map
        :db/id %2
        :urn/type :urn.type/issn
        :urn/entityType :urn.entityType/journal
        :urn/name (-> journal (t/get-item-rel :title) first :value)
        :urn/value %1
        :sameAs (clojure.set/difference (set journal-tempids) (set [%2]))
        :hasPart work-tempid)
      issns
      journal-tempids)
     [{:db/id work-tempid
       :isPartOf journal-tempids}])))
;     (map #(hash-map :db/id work-tempid :isPartOf %) journal-tempids))))

(defn work-citation->urn-datums [work-tempid citation]
  (when-let [doi (-> citation (t/get-item-ids :long-doi) first)]
    (let [cited-work-tempid (d/tempid :db.part/user)]
      [{:db/id cited-work-tempid
        :urn/type :urn.type/doi
        :urn/entityType :urn.entityType/work
        :urn/value doi
        :isCitedBy work-tempid}
       {:db/id work-tempid
        :cites cited-work-tempid}])))

(defn work-relation->urn-datums [work-tempid relation]
  (when-let [doi (-> relation :value)]
    (let [related-work-tempid (d/tempid :db.part/user)]
      [{:db/id work-tempid
        (-> relation :rel-type ->rel) related-work-tempid}
       {:db/id related-work-tempid
        :urn/value doi
        :urn/type :urn.type/doi
        :urn/entityType :urn.entityType/work
        (-> relation :rel-type ->rel relation-antonyms) work-tempid}])))

(defn work-item->urn-datums [item source]
  (let [work-tempid (d/tempid :db.part/user)]
    (concat
     [{:db/id work-tempid
       :urn/type :urn.type/doi
       :urn/entityType :urn.entityType/work
       :urn/name (or (-> item (t/get-item-rel :title) first :value) "")
       :urn/source source
       :urn/value (-> item (t/get-item-ids :long-doi) first)}]
     (mapcat (partial update-policy->urn-datums work-tempid)
             (t/get-item-rel item :update-policy))
     (mapcat (partial update->urn-datums work-tempid)
             (t/get-item-rel item :updates))
     (mapcat (partial funder->urn-datums work-tempid)
             (t/get-item-rel item :funder))
     (mapcat (partial author->urn-datums work-tempid)
             (t/get-item-rel item :author))
     (mapcat (partial editor->urn-datums work-tempid)
             (t/get-item-rel item :editor))
     (mapcat (partial work-citation->urn-datums work-tempid)
             (t/get-item-rel item :citation))
     (mapcat (partial work-relation->urn-datums work-tempid)
             (t/get-item-rel item :rel))
     (mapcat (partial journal->urn-datums work-tempid)
             (t/find-items-of-subtype item :journal)))))

(defn add-work-centered-tree! 
  "Add a work-centered item tree to datomic."
  [item-tree source]
  @(d/transact
    (conf/get-service :datomic)
    (work-item->urn-datums item-tree source)))

(defn find-all-citing-works []
  (d/q '[:find ?citing-urn ?citing-value ?citing-name
         :where 
         [_ :isCitedBy ?citing-urn]
         [?citing-urn :urn/value ?citing-value]
         [?citing-urn :urn/name ?citing-name]]
       (d/db (conf/get-service :datomic))))

(defn find-all-cited-works []
  (d/q '[:find ?cited-urn ?cited-value
         :where
         [_ :cites ?cited-urn]
         [?cited-urn :urn/value ?cited-value]]
       (d/db (conf/get-service :datomic))))

(defn find-all-funding-orgs []
  (d/q '[:find ?funding-org ?funding-value ?funding-name
         :where
         [_ :isFundedBy ?funding-org]
         [?funding-org :urn/value ?funding-value]
         [?funding-org :urn/name ?funding-name]]
       (d/db (conf/get-service :datomic))))

(defn find-all-funded-works []
  (d/q '[:find ?funded-work ?funded-value
         :where
         [_ :funds ?funded-work]
         [?funded-work :urn/value ?funded-value]]
       (d/db (conf/get-service :datomic))))

(defn find-all-updated-works []
  (d/q '[:find ?updated-work ?updated-value
         :where
         [_ :isUpdateTo ?updated-work]
         [?updated-work :urn/value ?updated-value]]
       (d/db (conf/get-service :datomic))))

(defn find-all-authoring-people []
  (d/q '[:find ?authoring-person ?authoring-value ?authoring-name
         :where
         [_ :isCreatedBy ?authoring-person]
         [?authoring-person :urn/value ?authoring-value]
         [?authoring-person :urn/name ?authoring-name]]
       (d/db (conf/get-service :datomic))))

(defn find-all-authored-works []
  (d/q '[:find ?authored-work ?authored-value
         :where
         [_ :created ?authored-work]
         [?authored-work :urn/value ?authored-value]]
       (d/db (conf/get-service :datomic))))

(defn find-all-urns []
  (d/q '[:find ?urn
         :where [_ :urn/value ?urn]]
       (d/db (conf/get-service :datomic))))

(defn count-all-urns []
  (d/q '[:find (count ?urn)
         :where [_ :urn/value ?urn]]
       (d/db (conf/get-service :datomic))))

(defn find-all-urns-from-source [source]
  (d/q '[:find ?urn
         :in $ ?source
         :where 
         [?something :urn/value ?urn]
         [?something :urn/source ?source]]
       (d/db (conf/get-service :datomic))
       source))

(defn find-all-journals []
  (d/q '[:find ?urn-value
         :where
         [?urn :urn/type :urn.type/issn]
         [?urn :urn/value ?urn-value]]
       (d/db (conf/get-service :datomic))))

(defn find-all-works-with-relation [relation]
  (d/q '[:find ?relatee-urn ?relation ?related-urn
         :in $ ?relation
         :where
         [?relatee ?relation ?related]
         [?related :urn/value ?related-urn]
         [?relatee :urn/value ?relatee-urn]]
       (d/db (conf/get-service :datomic))
       relation))

(defn find-all-non-source-works-with-relation [relation]
  (d/q '[:find ?relatee-urn ?relation ?related-urn
         :in $ ?relation
         :where
         [(missing? $ ?related :urn/source)]
         [?relatee ?relation ?related]
         [?related :urn/value ?related-urn]
         [?relatee :urn/value ?relatee-urn]]
       (d/db (conf/get-service :datomic))
       relation))

(defn find-most-cited-works [limit]
  (->> (d/q '[:find ?urn-value (distinct-count ?citing-urn-value)
              :where
              [?cited-urn :urn/value ?urn-value]
              [?citing-urn-value :cites ?cited-urn]]
            (d/db (conf/get-service :datomic)))
       (take limit)
       (sort-by second)))

(defn count-no-source-rels [rel]
  (d/q '[:find (count ?citing-urn)
         :in $ ?relation 
         :where
         [?citing-urn ?relation ?citee-urn]
         [?citing-urn :urn/source :urn.source/datacite]
         [(missing? $ ?citee-urn :urn/source)]]
       (d/db (conf/get-service :datomic))
       rel))

(defn count-crossref-source-rels [rel]
  (d/q '[:find (count ?citing-urn)
         :in $ ?relation 
         :where
         [?citing-urn ?relation ?citee-urn]
         [?citing-urn :urn/source :urn.source/datacite]
         [?cited-urn :urn/source :urn.source/crossref]]
       (d/db (conf/get-service :datomic))
       rel))

(defn with-all-relations [finding-fn]
  (mapcat finding-fn relation-types))
  ;(mapcat finding-fn (keys relation-one-way-antonyms)))

(defn with-all-work-to-work-relations [finding-fn]
  (mapcat finding-fn
          (clojure.set/difference (set (keys relation-one-way-antonyms))
                                  (set [:created :edited :isCreatedBy :isEditedBy]))))

(defn describe-urn [urn]
  (d/q '[:find ?prop-name ?val
         :in $ ?urn
         :where 
         [?something :urn/value ?urn]
         [?something ?prop ?val]
         [?prop :db/ident ?prop-name]]
       (d/db (conf/get-service :datomic))
       urn))



  
         





