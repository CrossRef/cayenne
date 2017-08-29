(ns cayenne.tasks.geoname
  (:require [cayenne.rdf :as rdf]
            [clojure.core.memoize :as memoize]))

(defn get-geoname-name [url]
  (let [rdf-model (rdf/document->model (java.net.URL. url))
        name-property (rdf/get-property "http://www.geonames.org/ontology#"
                                        rdf-model
                                        "name")]
    (-> rdf-model
        (rdf/select :predicate name-property)
        (rdf/objects)
        (first)
        (str))))

(def get-geoname-name-memo (memoize/lru get-geoname-name))

(defn clear! []
  (memoize/memo-clear! get-geoname-name-memo))

  
