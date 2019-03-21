(ns cayenne.pagination-test
  "Pagination across all of the available resources."
  (:require [cayenne.api-fixture :refer [empty-api api-get]]
            [cayenne.item-tree :as itree]
            [cayenne.elastic.index :as ei]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [ring.mock.request :as mock]))

(defn insert-works!
  "Insert a number of works with different numbered titles. Return the set of DOIs."
  [num-works]
  ; NB itree-processing code only recognises dx.doi.org resolver currently.
  (let [dois (doall (map #(str "http://dx.doi.org/10.5555/" %)
                         (range num-works)))
        works (map (fn [doi]
                       (-> (itree/make-item :work :journal-article)
                           (itree/add-relation :published
                                               {:type :date
                                                :day nil
                                                :month 1
                                                :year 2017
                                                :time-of-year nil})
                           (itree/add-id doi)))
                   dois)]

    (doseq [xs (partition-all 100 works)]
      (ei/index-items xs))
    (user/flush-elastic)

    ; We know that all are unique because of the way we constructed them.
    ; Using a set makes the data more useful in tests.
    (set dois)))

(defn dois-from-response
  "Retrieve set of DOIs from an API response, in URL form."
  [response]
  (->> response
       :items
       (map :DOI)
       ; DOIs as returned are in non-URL form so transform
       ; into full URLs for comparison.
       (map (partial str "http://dx.doi.org/"))
       set))

(deftest ^:integration works-pagination

  ; Make sure there are no works left between tests.
  (user/delete-works)

  ; Create a known set of pages, two tests to retrieve different ways.
  ; Use some awkward numbers to prevent assumptions about
  ; standard page sizes.
  (let [num-works 4321
        page-size 101
        num-pages (inc (quot num-works page-size))

        ; Insert this many DOIs' worth.
        inserted-dois (insert-works! num-works)]

    (testing "All works can be found using rows and offset."
             (let [found-dois (atom #{})]

               (doseq [page (range num-pages)]
                  (let [response (api-get "/v1/works"
                                  :query-params {:rows page-size
                                  :offset (* page-size page)})

                      dois-in-page (dois-from-response response)]

                  (swap! found-dois
                         #(clojure.set/union % dois-in-page))))
                (is (= @found-dois inserted-dois)
                    "Works for every DOI should be returned via page and offset")))

    (testing "All works can be found using cursor."
             (let [found-dois (atom #{})]
               ; Loop through pages using cursor.
               (loop [cursor "*"]
                 (let [response (api-get "/v1/works"
                                         :query-params {:cursor cursor}
                                         :rows page-size)
                       next-cursor (:next-cursor response)
                       dois-in-page (dois-from-response response)]

                   (swap! found-dois
                          #(clojure.set/union % dois-in-page))

                   ; Stop when we get an empty page worth of data.
                   ; Cursor may or may not be returned in this case,
                   ; we don't care.
                   (when-not (empty? dois-in-page)
                     (recur next-cursor))))

               (is (= @found-dois inserted-dois)
                   "Works for every DOI should be returned via cursor.")))))
(use-fixtures
 :once
 empty-api)
