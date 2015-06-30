(ns cayenne.api.auth.token
  (:require [cayenne.conf :as conf]))

;; For now, we are using a temporary token system for limited use of
;; the feed API.

(def token-dictionary
  (-> (conf/get-resource :tokens)
      slurp
      read-string))

(defn authenticated? [user token]
  (when-let [token-info (get token-dictionary token)]
    (when (some #{user} (:providers token-info))
      [user token])))
