(ns cayenne.data.coverage)

(defn- remove-zero-count-nodes [m]
  (reduce-kv
   (fn [m k v]
     (if (pos? (:_count v))
       (assoc m k (dissoc  v :_count))
       m))
   {}
   m))

(defn build-type-counts [m]
  (reduce-kv
   (fn [m k v]
     (if (pos? (:_count v))
       (assoc m k (:_count v))
       m))
   {}
   m))

(defn coverage [coverage-doc coverage-type]
  (reduce-kv
   (fn [m k v]
     (-> m
         (assoc-in [:coverage (keyword (str (name k) "-" (name coverage-type)))] v)
         (assoc-in [:flags (keyword (str "deposits-" (name k) "-" (name coverage-type)))] (pos? v))))
   {:coverage {}
    :flags {:deposits (> (apply + (map :_count (vals (get-in coverage-doc [:coverage :all])))) 0)
            :deposits-articles (or (> (get-in coverage-doc [:coverage :all :journal-article :_count]) 0) false)}}
   (dissoc (get-in coverage-doc [:coverage coverage-type :all]) :_count)))

(defn coverage-type [coverage-doc]
  (-> (:coverage coverage-doc)
      (update-in [:all] dissoc :all)
      (update-in [:backfile] dissoc :all)
      (update-in [:current] dissoc :all)
      (update-in [:all] remove-zero-count-nodes)
      (update-in [:backfile] remove-zero-count-nodes)
      (update-in [:current] remove-zero-count-nodes)))

(defn type-counts [coverage-doc]
  (-> (:coverage coverage-doc)
      (update-in [:all] dissoc :all)
      (update-in [:backfile] dissoc :all)
      (update-in [:current] dissoc :all)
      (update-in [:all] build-type-counts)
      (update-in [:current] build-type-counts)
      (update-in [:backfile] build-type-counts)))

