(defproject crossref/cayenne "0.1.0"
  :description "Index and serve CrossRef metadata"
  :url "http://github.com/CrossRef/cayenne"
  :signing {:gpg-key "labs@crossref.org"}
  :repl-options {:port 9494 :init-ns cayenne.user}
  :main cayenne.production
  :jvm-opts ["-XX:+UseG1GC"]
  :plugins [[lein-daemon "0.5.4"]]
  :resource-paths ["csl/styles" "csl/locales" "resources"]
  :daemon {:cayenne {:ns cayenne.production
                     :pidfile "cayenne.pid"}}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.datomic/datomic-free "0.9.4880.2"
                  :exclusions [org.slf4j/log4j-over-slf4j]]
                 [de.undercouch/citeproc-java "0.6"]
                 [org.jbibtex/jbibtex "1.0.14"]
                 [xml-apis "1.4.01"]
                 [metrics-clojure "1.0.1"]
                 [metrics-clojure-ring "1.0.1"]
                 [com.taoensso/timbre "2.1.2"]
                 [irclj "0.5.0-alpha2"]
                 [org.apache.solr/solr-solrj "4.3.0"]
                 [clojurewerkz/quartzite "1.0.1"]
                 [congomongo "0.4.1"]
                 [enlive "1.1.1"]
                 [htmlcleaner "2.2.4"]
                 [org.apache.jena/jena-core "2.10.1"]
                 [xom "1.2.5"]
                 [clj-http "0.7.2"]
                 [clj-time "0.6.0"]
                 [org.clojure/core.incubator "0.1.2"]
                 [org.clojure/data.json "0.2.0"]
                 [org.clojure/data.xml "0.0.7"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/core.memoize "0.5.3"]
                 [org.clojure/math.combinatorics "0.0.4"]
                 [com.novemberain/langohr "1.4.1"]
                 [liberator "0.10.0"]
                 [compojure "1.1.5"]
                 [ring "1.1.0"]
                 [ring-basic-authentication "1.0.5"]
                 [http-kit "2.1.16"]
                 [instaparse "1.2.14"]
                 [com.github.kyleburton/clj-xpath "1.4.3"]
                 [kjw/ring-logstash "0.1.3"]
                 [crossref/heartbeat "0.1.1"]
                 [robert/bruce "0.7.1"]])


