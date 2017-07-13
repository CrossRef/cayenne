(ns cayenne.tasks.category
  (:use [cayenne.ids.issn :only [normalize-issn]]
        [cayenne.item-tree])
  (:require [somnium.congomongo :as m]
            [clojure.core.memoize :as memoize]
            [dk.ative.docjure.spreadsheet :as sheet]
            [cayenne.conf :as conf]
            [clojure.string :as string]))

(defn get-category-name [category]
  (let [code (String/valueOf category)]
    (m/with-mongo (conf/get-service :mongo)
      (-> (m/fetch-one :categories :where {:code code}) (:name)))))

(defn get-issn-categories [issn]
  (let [norm-issn (normalize-issn issn)]
    (m/with-mongo (conf/get-service :mongo)
      (->> (m/fetch-one :issns :where {"$or" [{:p_issn norm-issn} {:e_issn norm-issn}]})
          (:categories)
          (map #(Integer/parseInt %))))))

(def get-category-name-memo (memoize/memo-lru get-category-name))

(def get-issn-categories-memo (memoize/memo-lru get-issn-categories))

(defn clear! []
  (memoize/memo-clear! get-category-name-memo)
  (memoize/memo-clear! get-issn-categories-memo))

(defn apply-to
  "Merge categories into an item if it is a journal item."
  ([item]
     (if (= :journal (get-item-subtype item))
       (do 
         (let [issns (map normalize-issn (get-item-ids item :issn))
               categories (set (mapcat get-issn-categories-memo issns))]
           (assoc item :category (map get-category-name-memo categories))))
       item))
  ([id item]
   [id (apply-to item)]))

(defn normalize-journal [m]
  (cond->
      (-> m
          (update-in [:categories]
                     #(->> (string/split % #";")
                           (map string/trim)
                           (filter (complement string/blank?))
                           (map (fn [n] (str (Integer/parseInt n))))))
          (update-in [:p_issn] normalize-issn)
          (update-in [:e_issn] normalize-issn))

    (nil? (:p_issn m))
    (dissoc m :p_issn)

    (nil? (:e_issn m))
    (dissoc m :e_issn)))

(defn normalize-category [m]
  (update-in m [:code] (comp str int)))

(defn load-categories [xls-file & {:keys [journals-sheet-name
                                          categories-sheet-name]
                                   :or {journals-sheet-name
                                        "Scopus Sources April 2017"
                                        categories-sheet-name
                                        "ASJC classification codes"}}]
  (let [doc (sheet/load-workbook xls-file)
        journals (->> doc
                      (sheet/select-sheet journals-sheet-name)
                      (sheet/select-columns {:C :p_issn
                                             :D :e_issn
                                             :AD :categories})
                      (drop 1)
                      (map normalize-journal))
        categories (->> doc
                        (sheet/select-sheet categories-sheet-name)
                        (sheet/select-columns {:A :code :B :name})
                        (drop 1)
                        (filter #(-> % :code nil? not))
                        (map normalize-category))]

    (m/with-mongo (conf/get-service :mongo)
      (doseq [category categories]
        (try 
          (m/update! :categories {:code (:code category)} category :upsert true)
          (catch Exception e
            (println "Exception on insert of " category)
            (println e))))
      (doseq [journal journals]
        (try
          (m/update! :issns {:p_issn (:p_issn journal)} journal :upsert true)
          (m/update! :issns {:e_issn (:e_issn journal)} journal :upsert true)
          (catch Exception e
            (println "Exception on insert of " journal)
            (println e)))))))
                           
      
       
       
  

