(ns cayenne.item-tree)

(defn item-seq 
  [item]
  (cons item (lazy-seq (flatten (map item-seq (flatten (vals (:rel item))))))))

(defn find-item [item match-fn]
  (first (filter match-fn (item-seq item))))

(defn find-item-of-subtype [item subtype]
  (find-item item #(= (:subtype %) subtype)))

(defn find-item-of-type [item type]
  (find-item item #(= (:type %) type)))

; defn issns dois etc
