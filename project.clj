(defproject cayenne "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repl-options {:port 9494 :init-ns cayenne.core}
  ;:jvm-opts ["-Xms2G" "-Xmx18G" "-XX:+UseG1GC"]
  :resource-paths ["local/harvester2.jar"
                   "local/log4j-1.2.12.jar"
                   "local/xalan.jar"
                   "local/xercesImpl.jar"
                   "local/xml-apis.jar"
                   "res"]
  :plugins [[codox "0.6.4"]]
  :dependencies [[riemann-clojure-client "0.2.1"]
                 [congomongo "0.4.1"]
                 [enlive "1.1.1"]
                 [htmlcleaner "2.2.4"]
                 [xom "1.2.5"]
                 [clj-http "0.7.2"]
                 [org.clojure/core.incubator "0.1.2"]
                 [org.clojure/clojure "1.4.0"]
                 [org.clojure/data.json "0.2.0"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/tools.trace "0.7.5"]
                 [org.clojure/core.memoize "0.5.3"]
                 [org.neo4j/neo4j "1.9.RC1"]
                 [org.neo4j.app/neo4j-server "1.9.RC1" :classifier "static-web"]
                 [org.neo4j.app/neo4j-server "1.9.RC1"]])
