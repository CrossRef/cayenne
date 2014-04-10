(ns cayenne.api.auth.crossref
  (:require [cayenne.conf :as conf]
            [org.httpkit.client :as hc]))

(defn authenticated? [user pass]
  (let [pid (str user ":" pass)
        query-params {:rtype "prefixes" :pid pid}]
    (when (-> (conf/get-param [:upstream :crossref-auth])
              (hc/get {:query-params query-params})
              deref
              :status
              (= 200))
      [user pass])))

