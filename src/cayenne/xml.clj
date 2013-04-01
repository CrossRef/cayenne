(ns cayenne.xml
  (:require [clojure.data.json :as json])
  (:require [clojure.java.io :as io]))

(defn- root-element? [^nu.xom.Element element]
  (instance? nu.xom.Document (.getParent element)))

(defn- name-eq? [^nu.xom.Node node ^String name]
  (and (= (type node) nu.xom.Element) (= (.getLocalName node) name)))

(defn- nodes->str [nodes]
  (apply str (map #(.getValue %) nodes)))

(defn- child-seq [^nu.xom.Node node]
  (if (nil? node)
    []
    (map #(.getChild node %) (range 0 (.getChildCount node)))))

(defn- descendant-seq* [nodes where-fn]
  (let [children (flatten (map child-seq nodes))
        rest (filter #(not (where-fn %)) children)
        stoppers (filter where-fn children)]
    (if (seq rest)
      (cons stoppers (lazy-seq (descendant-seq* rest where-fn)))
      stoppers)))

(defn- descendant-seq [nodes where-fn]
  (flatten (descendant-seq* nodes where-fn)))

(defn- attribute->str [attribute]
  (when (not (nil? attribute))
    (.getValue attribute)))

(defn process-xml [^java.io.Reader rdr tag-name process-fn]
  (let [keep? (atom false)
        empty (nu.xom.Nodes.)
        factory (proxy [nu.xom.NodeFactory] []
                  (startMakingElement [^String name ^String ns]
                    (when (= (last (.split name ":")) tag-name)
                      (reset! keep? true))
                    (proxy-super startMakingElement name ns))
                  (finishMakingElement [^nu.xom.Element element]
                    (when (= (.getLocalName element) tag-name)
                      (process-fn element)
                      (reset! keep? false))
                    (if (or @keep? (root-element? element))
                      (proxy-super finishMakingElement element)
                      empty)))]
    (.build (nu.xom.Builder. factory) rdr)))

(defrecord SelectorContext [nodes descending?])

(defn- xselect-result [out-val]
  (if (= SelectorContext (type out-val))
    (if (nil? (:nodes out-val))
      []
      (:nodes out-val))
    out-val))

(defn- xselect* [^SelectorContext context selector]
  (let [nodes (:nodes context)
        descending? (:descending? context)]
    (cond
     (vector? selector)
     (let [f (first selector)]
       (cond 
        (= :has f)
        (->SelectorContext 
         (filter #(= (.getAttribute % (second selector)) nil) nodes)
         false)
        (= := f)
        (->SelectorContext
         (filter #(= (.getAttribute % (second selector)) (nth selector 2)) nodes)
         false)
        :else
        (map #(-> % (.getAttribute f) (attribute->str)) nodes)))

     (= :> selector)
     (->SelectorContext nodes true)

     (= :text selector)
     (map (comp nodes->str child-seq) nodes)

     (and descending? (= java.lang.String (type selector)))
     (->SelectorContext
      (descendant-seq nodes #(name-eq? % selector))
      false)

     (= java.lang.String (type selector))
     (->SelectorContext
      (filter #(name-eq? % selector)
              (flatten (map child-seq nodes)))
      false))))

(defn xselect [nodes & path]
  (if (nil? nodes)
    []
    (let [node-seq (if (seq? nodes) nodes (cons nodes nil))
          initial (->SelectorContext node-seq false)
          result (reduce xselect* initial path)]
      (xselect-result result))))

(defn xselect1 [& args]
  (first (apply xselect args)))

