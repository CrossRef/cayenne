(ns cayenne.elastic.util
  (:require [qbits.spandex :as elastic]
            [clojure.data.json :as json]))

(defn raw-jsons [jsons]
  (-> (apply str
             (->> jsons
                  (map json/write-str)
                  (interpose "\n")))
      (str "\n")
      elastic/raw))

