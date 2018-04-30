(ns cayenne.version)

(def version (System/getProperty "cayenne.version"))
(assert version "Failed to detect version.")

