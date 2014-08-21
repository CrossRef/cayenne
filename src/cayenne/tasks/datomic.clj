(ns cayenne.tasks.datomic
  (:require [cayenne.conf :as conf]
            [cayenne.item-tree :as t]
            [datomic.api :as d]))

(def relation-types
  [:isCitedBy
   :cites
   :isSupplementTo
   :isSupplementedBy
   :isContinuedBy
   :continues
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

(def urn-schema
  [{:db/id #db/id[:db.part/db]
    :db/ident :urn/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many
    :db/index true
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
    :db/ident :urn.entityType/journal}])

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

(defn recreate-db! []
  (let [uri (conf/get-param [:service :datomic :url])]
    (d/delete-database uri)
    (d/create-database uri)
    (let [conn (d/connect uri)]
      (d/transact conn (concat urn-schema relations-schema))
      (conf/set-service! :datomic conn))))

(defn funder->urn-datums [work-tempid funder]
  (when-let [funder-doi (-> funder (t/get-item-ids :long-doi) first)]
    (let [funder-tempid (d/tempid :db.part/user)]
      [{:db/id funder-tempid
        :urn/type :urn.type/doi
        :urn/entityType :urn.entityType/org
        ;:urn/name (:name funder)
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
        :urn/value orcid
        :edited work-tempid}
       {:db/id work-tempid
        :isEditedBy editor-tempid}])))

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
        :urn/value %1
        :sameAs journal-tempids
        :hasPart work-tempid)
      issns
      journal-tempids)
     (map #(hash-map :db/id work-tempid :isPartOf %) journal-tempids))))

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

(defn work-item->urn-datums [item source]
  (let [work-tempid (d/tempid :db.part/user)]
    (concat
     [{:db/id work-tempid
       :urn/type :urn.type/doi
       :urn/entityType :urn.entityType/work
       ;:urn/name (get-title item)
       :urn/source source
       :urn/value (-> item (t/get-item-ids :long-doi) first)}]
     (mapcat (partial funder->urn-datums work-tempid)
             (t/get-item-rel item :funder))
     (mapcat (partial author->urn-datums work-tempid)
             (t/get-item-rel item :author))
     (mapcat (partial editor->urn-datums work-tempid)
             (t/get-item-rel item :editor))
     (mapcat (partial work-citation->urn-datums work-tempid)
             (t/get-item-rel item :citation))
     (mapcat (partial journal->urn-datums work-tempid)
             (t/find-item-of-subtype item :journal)))))

(defn add-work-centered-tree! 
  "Add a work-centered item tree to datomic."
  [item-tree source]
  @(d/transact
    (conf/get-service :datomic)
    (work-item->urn-datums item-tree source)))

