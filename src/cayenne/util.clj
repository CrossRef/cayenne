(ns cayenne.util)

(defn assoc-when
  "Like assoc but only assocs when value is truthy"
  [m & kvs]
  (assert (even? (count kvs)))
  (into m
        (for [[k v] (partition 2 kvs)
         :when v]
     [k v])))

(defn without-nil-vals [record]
  (reduce (fn [m [k v]] (if (nil? v) (dissoc m k) m)) record record))

(defn without-keyword-vals [record]
  (reduce (fn [m [k v]] (if (keyword? v) (assoc m k (name v)) m)) record record))

(defn with-java-array-vals [record]
  (reduce (fn [m [k v]] (if (or (vector? v) (seq? v)) (assoc m k (into-array v)) m)) record record))

(defn with-safe-vals [record]
  (-> record (without-nil-vals) (without-keyword-vals) (with-java-array-vals)))

; File utils
; ---------------------------------------------------------------

(defn file-of-kind? [kind path]
  "Does the path point to a file that ends with kind?"
  (and (.isFile path) (.endsWith (.getName path) kind)))

(defn file-kind-seq [kind file-or-dir count]
  "Return a seq of all xml files under the given directory."
  (if (= count :all)
    (->> (file-seq file-or-dir)
         (filter #(file-of-kind? kind %)))
    (->> (file-seq file-or-dir)
         (filter #(file-of-kind? kind %))
         (take count))))

