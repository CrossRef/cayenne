(ns cayenne.pagination-test
  "Pagination across all of the available resources."
  (:require [cayenne.api-fixture :refer [empty-api api-get]]
            [cayenne.item-tree :as itree]
            [cayenne.elastic.index :as ei]
            [clojure.test :refer [use-fixtures deftest testing is]]
            [ring.mock.request :as mock]))

(defn insert-works!
  "Insert a number of works with different numbered titles. Return the set of DOIs."
  ([num-works]
   (insert-works! num-works "10.5555"))

  ([num-works prefix]
   ; NB itree-processing code only recognises dx.doi.org resolver currently.
   (let [dois (doall (map #(str "http://dx.doi.org/" prefix "/" %)
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
     (set dois))))

(defn normalize-dois
  [dois]
  (map (partial str "http://dx.doi.org/") dois))

(defn fetch-cursor-lazy
  "Return all results using cursor in a lazy seq."
  ([path query-params]
   (fetch-cursor-lazy path query-params "*"))
  ([path query-params cursor]
    (let [response (api-get path
                            :query-params
                            (assoc query-params :cursor cursor))
           cursor (:next-cursor response)]

      (when-let [items (-> response :items seq )]

        (lazy-cat items (fetch-cursor-lazy path query-params cursor))))))

(deftest ^:integration works-pagination-equivalent

  ; Make sure there are no works left between tests.
  (user/delete-works)

  ; Create a known set of pages, two tests to retrieve different ways.
  ; Use some awkward numbers to prevent assumptions about
  ; standard page sizes.
  (let [num-works 4321
        page-size 101
        num-pages (inc (quot num-works page-size))

        ; Insert this many DOIs' worth.
        inserted-dois (insert-works! num-works)

        ; Store both sets of responses.
        found-by-offset (atom [])
        found-by-cursor (atom [])]

    (testing "All works can be found using rows and offset."
             (let [found-dois (atom [])]

               (doseq [page (range num-pages)]
                  (let [response (api-get "/v1/works"
                                  :query-params {:rows page-size
                                  :offset (* page-size page)})

                      dois-in-page (->> response :items (map :DOI) normalize-dois)]

                  ; Store DOIs found so we can check the right results were found.
                  (swap! found-dois
                         #(concat % dois-in-page))

                  ; Also store literal results to compare between retrieval methods.
                  (swap! found-by-offset
                         #(concat % (-> response :items)))))

                (is (= (set @found-dois) inserted-dois)
                    "Works for every DOI should be returned via page and offset")))

    (testing "All works can be found using cursor."
             (let [found-works (fetch-cursor-lazy "/v1/works" {:rows page-size})
                   found-dois (->> found-works (map :DOI) normalize-dois)]

               ; Also store literal results to compare between retrieval methods.
               (reset! found-by-cursor found-works)

               (is (= (set found-dois) inserted-dois)
                   "Works for every DOI should be returned via cursor.")))

    (testing "Results by offset and cursor are identical (except score)."
             ; If we don't check this, then they could be both empty by mistake
             ; and give a false positive.
             (is (not-empty @found-by-cursor)
                 "Some results were returned.")

             (is (not-empty @found-by-offset)
                 "Some results were returned.")

             ; Both tests ran first.
             (is (= (->> @found-by-offset
                        (map #(dissoc % :score))
                        set)

                    (-> @found-by-cursor
                        set))
                 "Both result sets identical, except for :score, which isn't present
                  in cursor results."))))

(deftest ^:integration cursor-results-frozen
  ; Ensure that the API represents the functionality defined by the Elastic Search spec.
  ; The results that are returned from a scroll request reflect the state of the index at
  ; the time that the initial search request was made, like a snapshot in time. Subsequent
  ; changes to documents (index, update or delete) will only affect later search requests.
  ;  - https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html

  (testing "When a cursor session starts, results are frozen."
           (user/delete-works)

           (let [num-works 1000
                 page-size 100
                 first-chunk 500

                 ; Insert an initial thousand works.
                 inserted-dois (insert-works! 1000 "10.5555")]

              (is (= (count inserted-dois)
                      1000)
                   "A thousand DOIs are inserted as expected.")

             ; Retrieve all the results, but in two chunks.
             ; Lazy sequence, so queries aren't executed until it's realized.
             (let [lazy-results (fetch-cursor-lazy "/v1/works" {:rows 100})
                   ; Retrieve the first 400, we'll get the last 600 later.
                   [first-half second-half] (split-at 400 lazy-results)]

               ; First chunk of results return as many as we requested.
               (is (= (count first-half) 400))

               ; Second chunk of results hasn't been requested yet.
               ; Check this to ensure that the requests haven't been made yet.
               (is (not (realized? second-half)))

               ; Now insert a second load of results mid-pagination.
               (let [newly-inserted-dois (insert-works! 100 "10.6666")

                     ; Before continuing, ensure that a newly executed query is able
                     ; to retrieve all results.
                     newly-fetched-all (fetch-cursor-lazy "/v1/works" {:rows 100})]

                 (is (= (count newly-fetched-all) 1100)
                     "A newly-started query is able to retrieve all results.")

                 (is (= (count second-half) 600)
                     "Finishing off the pagination of the original query returns only
                      the set of results available at start of pagination."))))))

(use-fixtures
 :once
 empty-api)
