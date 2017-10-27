(ns cayenne.tasks.category
  (:use [cayenne.ids.issn :only [normalize-issn]]
        [cayenne.item-tree])
  (:require [somnium.congomongo :as m]
            [clojure.core.memoize :as memoize]
            [dk.ative.docjure.spreadsheet :as sheet]
            [cayenne.conf :as conf]
            [clojure.string :as string]
            [qbits.spandex :as elastic]
            [cayenne.elastic.util :as elastic-util]))

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

(def get-category-name-memo (memoize/lru get-category-name :lru/threshold 100))

(def get-issn-categories-memo (memoize/lru get-issn-categories :lru/threshold 100))

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

(defn index-subject-command [subject]
  (let [code (-> subject :code int)]
    [{:index {:_id code}}
     {:code code
      :high-code   (-> code (/ 100) int (* 100))
      :name   (-> subject :name string/trim)}]))

(defn update-journal-command [subject]
  ())

(defn index-subjects [& {:keys [xls-location
                                journals-sheet-name
                                categories-sheet-name]
                         :or {xls-location
                              (-> (conf/get-param [:location :scopus-title-list])
                                  (java.net.URL.))
                              journals-sheet-name
                              "Scopus Sources October 2017"
                              categories-sheet-name
                              "ASJC classification codes"}}]
  (with-open [xls-in (clojure.java.io/input-stream xls-location)]
    (let [doc (sheet/load-workbook-from-stream xls-in)
          journals (->> doc
                        (sheet/select-sheet journals-sheet-name)
                        (sheet/select-columns {:C :p_issn
                                               :D :e_issn
                                               :AD :categories})
                        (drop 1))
          subjects (->> doc
                        (sheet/select-sheet categories-sheet-name)
                        (sheet/select-columns {:A :code :B :name})
                        (drop 1)
                        (filter #(-> % :code nil? not)))]
      (elastic/request
       (conf/get-service :elastic)
       {:method :post
        :url "category/category/_bulk"
        :body (->> subjects
                   (map index-subject-command)
                   flatten
                   elastic-util/raw-jsons)}))))

(defn workbook-sheet-names [xls-file]
  (->> xls-file
       sheet/load-workbook
       sheet/sheet-seq
       (map #(.getSheetName %))))

    ;; (->> subjects (map index-subject-command) flatten)
    

    ;; (m/with-mongo (conf/get-service :mongo)
    ;;   (doseq [category categories]
    ;;     (try
    ;;       (let [n-category (normalize-category category)]
    ;;         (m/update! :categories
    ;;                    {:code (:code n-category)}
    ;;                    n-category
    ;;                    :upsert true))
    ;;       (catch Exception e
    ;;         (println "Exception on insert of " category)
    ;;         (println e))))
    ;;   (doseq [journal journals]
    ;;     (try
    ;;       (let [n-journal (normalize-journal journal)]
    ;;         (m/update! :issns
    ;;                    {:p_issn (:p_issn n-journal)}
    ;;                    n-journal
    ;;                    :upsert true)
    ;;         (m/update! :issns
    ;;                    {:e_issn (:e_issn n-journal)}
    ;;                    n-journal
    ;;                    :upsert true))
    ;;       (catch Exception e
    ;;         (println "Exception on insert of " journal)
    ;;         (println e)))))))
                           
      
       
       
  

