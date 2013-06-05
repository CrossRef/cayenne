(ns cayenne.util)

(defn assoc-when
  "Like assoc but only assocs when value is truthy"
  [m & kvs]
  (assert (even? (count kvs)))
  (into m
        (for [[k v] (partition 2 kvs)
         :when v]
     [k v])))

(defn keys-in
  "Return all keys in nested maps."
  [m]
  (if (map? m)
    (concat
     (keys m)
     (mapcat (comp keys-in (partial get m)) (keys m)))))

(defn map-diff 
  "Produce the list of keys in a but not in b."
  [a b]
  (filter #(not (get b %)) (keys a)))

(defn map-intersect 
  "Produce a list of keys present in a and b."
  [a b]
  (filter #(get a %) (keys b)))

(defn without-nil-vals
  "Dissoc any key val pairs where the val is nil."
  [record]
  (reduce (fn [m [k v]] (if (nil? v) (dissoc m k) m)) record record))

(defn without-keyword-vals
  "Convert all map values that are keywords into Java strings."
  [record]
  (reduce (fn [m [k v]] (if (keyword? v) (assoc m k (name v)) m)) record record))

(defn without-keyword-keys
  "Convert all map keys that are keywords into Java strings."
  [record]
  (reduce (fn [m [k v]] (if (keyword? k) (assoc m (name k) v) m)) record record))

(defn with-java-array-vals 
  "Convert all clojure vectors and seqs in a map to Java arrays."
  [record]
  (reduce 
   (fn [m [k v]] 
     (if (or (vector? v) (seq? v)) (assoc m k (into-array v)) m)) record record))

(defn patherize [coll]
  (reduce #(conj %1 (conj (vec (last %1)) %2)) [] coll))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Control flow stuff taken from Prismatic's plumbing lib

(defmacro ?>>
  "Conditional double-arrow operation (->> nums (?>> inc-all? map inc))"
  [do-it? f & args]
  `(if ~do-it?
     (~f ~@args)
     ~(last args)))

(defmacro ?>
  "Conditional single-arrow operation (-> m (?> add-kv? assoc :k :v))"
  [arg do-it? f & rest]
  `(if ~do-it?
     (~f ~arg ~@rest)
     ~arg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; File utils

(defn file-of-kind? 
  "Does the path point to a file that ends with kind?"
  [kind path]
  (and (.isFile path) (.endsWith (.getName path) kind)))

(defn file-kind-seq 
  "Return a seq of all xml files under the given directory."
  [kind file-or-dir count]
  (if (= count :all)
    (->> (file-seq file-or-dir)
         (filter #(file-of-kind? kind %)))
    (->> (file-seq file-or-dir)
         (filter #(file-of-kind? kind %))
         (take count))))

