(ns cayenne.corpus-test
  (:require [cayenne.api-fixture :refer [api-get api-with-works]]
            [cemerick.url :refer [url-encode]]
            [clj-http.client :as http]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [marge.core :refer [markdown]]))

(defn- take-dois [n]
  (->> (api-get (str "/v1/works?rows=10000&select=DOI,score&filter=type:journal-article"))
       :items
       (map :DOI)
       shuffle
       (take n)))

(defn- get-apa-citation [doi error-fn]
  (try (-> (http/get (str "http://data.crossref.org/" doi) {:accept "text/x-bibliography; style=apa"})
           :body
           (clojure.string/replace (str "doi:" doi) "")
           (->> (take 1000))
           (->> (apply str))
           url-encode)
       (catch Exception e
         (println (str "Problem getting apa for DOI: " doi))
         (error-fn doi))))

(defn- match-bibliographic [citation]
  (-> (api-get (str "/v1/works?rows=1&query.bibliographic=" citation))
      :items
      first
      (select-keys [:DOI :score])))

(deftest citation-matching

  (testing "bibliographic matches return expected results for DOIS"
    (with-redefs [cayenne.api.v1.query/max-rows 10000]
      (let [matches (atom {:ok [] :bad []})
            apa-problems (atom [])
            tolerance% 2
            dois (take-dois 5000)
            total-count (count dois)]
        (doseq [doi dois]
          (let [citation (get-apa-citation doi #(swap! apa-problems conj %))
                {:keys [DOI score]} (match-bibliographic citation)]
            (swap! matches (fn [x] (if (= doi DOI) (update x :ok conj [doi DOI citation score]) (update x :bad conj [doi DOI citation score]))))
            (Thread/sleep 1000)))
        (let [tried-to-match (- total-count (count @apa-problems))
              one-percent (float (/ tried-to-match 100))
              number-required (* one-percent (- 100 tolerance%))
              good-matches (count (:ok @matches))
              success-percent (* (float (/ tried-to-match good-matches)) 100)
              bad-matches (count (:bad @matches))
              sorted-matches (sort-by first (take 5000 (:ok @matches)))
              sorted-bad-matches (sort-by first (:bad @matches))]
          (print (markdown
                  [:p (str "Attempted to do citation matching for "
                           total-count " DOI records using a total corpus of "
                           (user/elastic-doc-count) " items")
                   :p (str "It was not possible to download an apa for "
                           (count @apa-problems) " DOI records.")
                   :table ["DOI" @apa-problems]
                   :p (str "Successfully matched "
                           good-matches
                           " DOI records. A sample of which are below:")
                   :table ["Original DOI" (mapv first sorted-matches)
                           "Matched DOI" (mapv second sorted-matches)
                           "Score" (mapv #(nth % 3) sorted-matches)]
                   :p (str "Unable to match " bad-matches " citations at the first attempt.")
                   :table ["Original DOI" (mapv first sorted-bad-matches)
                           "Matched DOI" (mapv second sorted-bad-matches)
                           "Score" (mapv #(nth % 3) sorted-bad-matches)]
                   :p (str "The total number of required matches for a "
                           tolerance% "% failure tolerance is "
                           number-required ", there were " good-matches
                           " successful matches which is " success-percent "%")]))
          (is (> good-matches number-required)))))))

(use-fixtures
  :once
  api-with-works)
