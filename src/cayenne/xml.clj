(ns cayenne.xml
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [metrics.meters :refer [defmeter] :as meter]
            [metrics.counters :refer [defcounter] :as counter]
            [clojure.string :as string]))

(defcounter [cayenne xml records-processed])
(defmeter [cayenne xml record-process-rate] "record-process-rate")

(defn- root-element? [^nu.xom.Element element]
  (instance? nu.xom.Document (.getParent element)))

(defn- name-eq? [^nu.xom.Node node ^String name]
  (and (= (type node) nu.xom.Element) (= (.getLocalName node) name)))

(defn- nodes->str [nodes]
  (apply str (map #(.getValue %) nodes)))

(defn- nodes->xml [nodes]
  (apply str (map #(.toXML %) nodes)))

(defn- nodes->plain [nodes]
  (->> nodes
       (filter #(= (type %) nu.xom.Text))
       (map #(.getValue %))
       (apply str)
       (.trim)))
     
(defn- child-seq [^nu.xom.Node node]
  (if node
    (map #(.getChild node %) (range 0 (.getChildCount node)))
    []))

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
  (when attribute
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
                      (do 
                        (counter/inc! records-processed)
                        (meter/mark! record-process-rate)
                        (process-fn element)
                        (reset! keep? false)))
                    (if (or @keep? (root-element? element))
                      (proxy-super finishMakingElement element)
                      empty)))]
    (.build (nu.xom.Builder. factory) rdr)))

(defn read-xml [^java.io.Reader rdr]
  (.getRootElement (.build (nu.xom.Builder.) rdr)))

(defrecord SelectorContext [nodes descending?])

(defn- xselect-result [out-val]
  (if (= SelectorContext (type out-val))
    (or (:nodes out-val) [])
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
         (filter #(not= (.getAttribute % (second selector)) nil) nodes)
         false)
        (= := f)
        (->SelectorContext
         (filter 
          #(= (-> % (.getAttribute (second selector)) (attribute->str)) (nth selector 2)) nodes)
         false)
        (= :has-not f)
        (->SelectorContext
         (filter #(= (.getAttribute % (second selector)) nil) nodes)
         false)
        :else
        (map #(-> % (.getAttribute f) (attribute->str)) nodes)))

     (= :> selector)
     (->SelectorContext nodes true)

     (= :text selector)
     (map (comp nodes->str child-seq) nodes)

     (= :plain selector)
     (map (comp nodes->plain child-seq) nodes)

     (= :xml selector)
     (map (comp nodes->xml child-seq) nodes)

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
  (if-not nodes
    []
    (let [node-seq (if (seq? nodes) nodes (cons nodes nil))
          initial (->SelectorContext node-seq false)
          result (reduce xselect* initial path)]
      (xselect-result result))))

(defn xselect1 [& args]
  (let [res (first (apply xselect args))]
    (if (string? res)
      (string/trim res)
      res)))

