(defproject cayenne "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://github.com/CrossRef/cayenne"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repl-options {:port 9494 :init-ns cayenne.action}
  :jvm-opts ["-XX:+UseG1GC"]
  :plugins [[codox "0.6.4"]]
  :dependencies [[com.taoensso/timbre "2.1.2"]
                 [clojurewerkz/neocons "1.1.0"]
                 [irclj "0.5.0-alpha2"]
                 [org.apache.solr/solr-solrj "4.3.0"]
                 [clojurewerkz/quartzite "1.0.1"]
                 [riemann-clojure-client "0.2.1"]
                 [congomongo "0.4.1"]
                 [enlive "1.1.1"]
                 [htmlcleaner "2.2.4"]
                 [org.apache.jena/jena-core "2.10.1"]
                 [xom "1.2.5"]
                 [clj-http "0.7.2"]
                 [clj-time "0.5.1"]
                 [org.clojure/core.incubator "0.1.2"]
                 [org.clojure/clojure "1.4.0"]
                 [org.clojure/data.json "0.2.0"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/tools.trace "0.7.5"]
                 [org.clojure/core.memoize "0.5.3"]
                 [org.clojure/math.combinatorics "0.0.4"]
                 [org.neo4j/neo4j "1.9.RC1"]
                 [org.neo4j.app/neo4j-server "1.9.RC1" :classifier "static-web"]
                 [org.neo4j.app/neo4j-server "1.9.RC1"]
                 [liberator "0.9.0"]
                 [compojure "1.1.5"]
                 [ring/ring-jetty-adapter "1.2.0"]])

