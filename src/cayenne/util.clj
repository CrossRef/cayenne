(ns cayenne.util
  (:require [clojure.string :as string]))

(defn assoc-when
  "Like assoc but only assocs when value is truthy"
  [m & kvs]
  (assert (even? (count kvs)))
  (into m
        (for [[k v] (partition 2 kvs)
         :when v]
          [k v])))

(defn assoc-str
  "Like assoc but only assocs when value is a non-blank string. Value is assoced
   as a trimmed string."
  [m & kvs]
  (assert (even? (count kvs)))
  (into m
        (for [[k v] (partition 2 kvs)
         :when (not (string/blank? v))]
          [k (string/trim v)])))

(declare parse-int-safe)

(defn assoc-int
  "Like assoc but only assocs when value is not nil. Value is assoced
   as a parsed int."
  [m & kvs]
  (assert (even? (count kvs)))
  (into m
        (for [[k v] (partition 2 kvs)
         :when (parse-int-safe v)]
          [k (parse-int-safe v)])))

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
  (reduce (fn [m [k v]] (if v m (dissoc m k))) record record))

(defn without-keyword-vals
  "Convert all map values that are keywords into Java strings."
  [record]
  (reduce (fn [m [k v]] (if (keyword? v) (assoc m k (name v)) m)) record record))

(defn with-java-array-vals 
  "Convert all clojure vectors and seqs in a map to Java arrays."
  [record]
  (reduce 
   (fn [m [k v]] 
     (if (or (vector? v) (seq? v)) (assoc m k (into-array v)) m)) record record))

(defn patherize [coll]
  (reduce #(conj %1 (conj (vec (last %1)) %2)) [] coll))

(defn update-vals [m keys f]
  (reduce #(update-in % [%2] f) m keys))

(defn update-keys [m f]
  (zipmap (map f (keys m)) (vals m)))

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

(defn file-ext [f]
  (last (clojure.string/split "." (.getName f))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse utils

(defn parse-int [s]
  (if (number? s)
    s
    (Integer. (re-find #"-?\d+" s))))

(defn parse-int-safe [s]
  (if (number? s)
    s
    (try (Integer. (re-find #"-?\d+" s)) (catch Exception e nil))))

(defn parse-float [s]
  (if (number? s)
    s
    (Float. (re-find #"-?[\d.]+" s))))

(defn parse-float-safe [s]
  (if (number? s)
    s
    (try (Float. (re-find #"-?[\d.]+" s)) (catch Exception e nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Misc

(defn simplify-name [name]
  (-> (.toLowerCase name)
      (.trim)
      (.replaceAll "\\)" "")
      (.replaceAll "\\(" "")
      (.replaceAll "," "")
      (.replaceAll "\\." "")
      (.replaceAll "'" "")
      (.replaceAll "\"" "")
      (.replaceAll "-" " ")))

(defn tokenize-name [name]
  (-> (simplify-name name)
      (string/replace #"[\(\)]" " ")
      (string/replace #"&" "& and")
      (string/split #"\s+")))

(defn slugify [uri]
  (when uri
    (string/replace uri #"[^a-zA-Z0-9]" "_")))

(defn ?- 
  "Return a fn that tries to take k out of a map, or returns
   a placeholder string if missing."
  [k]
  (fn [m]
    (if-let [v (get m k)]
      v
      "-")))

(defn ?fn- 
  "Return a fn that tries to take k out of a map, or returns
   a placeholder string if missing."
  [k]
  (fn [m]
    (if-let [v (k m)]
      v
      "-")))

(defn dissoc-in [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (clojure.core/get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn safe-trim [s]
  (when s (string/trim s)))
