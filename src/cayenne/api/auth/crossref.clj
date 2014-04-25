(ns cayenne.api.auth.crossref
  (:require [cayenne.conf :as conf]
            [org.httpkit.client :as hc]))

(defn authenticated? [user pass]
  (let [pid (str user ":" pass)
        query-params {:rtype "prefixes" :pid pid}]
    (when 
        (or (-> (conf/get-param [:upstream :crossref-auth])
                (hc/get {:query-params query-params})
                deref
                :status
                (= 200))
            (-> (conf/get-param [:upstream :crossref-test-auth])
                (hc/get {:query-params query-params})
                deref
                :status
                (= 200)))
      [user pass])))

