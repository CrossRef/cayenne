(ns cayenne.tasks.category
  (:use [cayenne.ids.issn :only [normalize-issn]]
        [cayenne.item-tree])
  (:require [dk.ative.docjure.spreadsheet :as sheet]
            [cayenne.conf :as conf]
            [clojure.string :as string]
            [qbits.spandex :as elastic]
            [cayenne.elastic.util :as elastic-util]
            [cayenne.ids.issn :as issn-id]))

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
     {:code      code
      :high-code (-> code (/ 100) int (* 100))
      :name      (-> subject :name string/trim)}]))

(defn index-subjects [& {:keys [xls-location
                                journals-sheet-name
                                categories-sheet-name]
                         :or {xls-location
                              (-> (conf/get-param [:location :scopus-title-list])
                                  (java.net.URL.))
                              categories-sheet-name
                              "ASJC classification codes"}}]
  (with-open [xls-in (clojure.java.io/input-stream xls-location)]
    (let [doc (sheet/load-workbook-from-stream xls-in)
          subjects (->> doc
                        (sheet/select-sheet categories-sheet-name)
                        (sheet/select-columns {:A :code :B :name})
                        (drop 1)
                        (filter #(-> % :code nil? not)))]
      (elastic/request
       (conf/get-service :elastic)
       {:method :post
        :url "/subject/subject/_bulk"
        :body (->> subjects
                   (map index-subject-command)
                   flatten
                   elastic-util/raw-jsons)}))))

(defn journal-id [issns]
  (-> (elastic/request
       (conf/get-service :elastic)
       {:method :get
        :url "/journal/journal/_search"
        :body (assoc-in
               {:_source [:id]}
               [:query :bool :should]
               (map #(hash-map :term {:issn.value %}) issns))})
      (get-in [:body :hits :hits])
      first
      (get-in [:_source :id])))

(defn update-journal-subjects [& {:keys [xls-location
                                         journals-sheet-name]
                                  :or {xls-location
                                       (-> (conf/get-param [:location :scopus-title-list])
                                           (java.net.URL.))
                                       journals-sheet-name
                                       "Scopus Sources October 2017"}}]
  (with-open [xls-in (clojure.java.io/input-stream xls-location)]
    (doseq [journal (->> xls-in
                         sheet/load-workbook-from-stream
                         (sheet/select-sheet journals-sheet-name)
                         (sheet/select-columns {:C :p-issn
                                                :D :e-issn
                                                :AE :subjects})
                         (drop 1))]
      (let [p-issn (-> journal :p-issn str issn-id/normalize-issn)
            e-issn (-> journal :e-issn str issn-id/normalize-issn)
            issns (cond-> []
                    (-> p-issn nil? not)
                    (conj p-issn)
                    (-> e-issn nil? not)
                    (conj e-issn))
            subject-codes (->> (string/split (:subjects journal) #";")
                               (map string/trim)
                               (filter (complement string/blank?)))
            subjects (map
                      #(hash-map
                        :code (-> % str Integer/parseInt)
                        :high-code (-> % str Integer/parseInt (/ 100) int (* 100)))
                      subject-codes)]
        (when-let [jid (journal-id issns)]
          (elastic/request
           (conf/get-service :elastic)
           {:method :post
            :url (str "/journal/journal/" jid "/_update")
            :body {:doc {:subject subjects}}}))))))

(defn workbook-sheet-names [xls-file]
  (->> xls-file
       sheet/load-workbook
       sheet/sheet-seq
       (map #(.getSheetName %))))
       
  

