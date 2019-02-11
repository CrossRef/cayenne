(defproject crossref/cayenne "1.2.1"
  :description "Index and serve CrossRef metadata"
  :url "http://github.com/CrossRef/cayenne"
  :repl-options {:port 9494 :init-ns cayenne.user}
  :main cayenne.production
  :jvm-opts ["-XX:+UseG1GC"]
  :resource-paths ["csl/styles" "csl/locales" "resources"]
  :daemon {:cayenne {:ns cayenne.production
                     :pidfile "cayenne.pid"}}
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :resource-paths ["dev-resources"]
                   :dependencies [[marge "0.11.0"]]}
             :prod {}
             :datomic
             {:repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                               :creds :gpg}}
              :dependencies [[com.datomic/datomic-pro "0.9.4894"
                              :exclusions [org.slf4j/log4j-over-slf4j]]]}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.clojure/tools.trace "0.7.8"]
                 [org.mozilla/rhino "1.7.7.1"]
                 [de.undercouch/citeproc-java "0.6"]
                 [org.jbibtex/jbibtex "1.0.14"]
                 [info.hoetzel/clj-nio2 "0.1.1"]
                 [xml-apis "1.4.01"]
                 [metrics-clojure "1.0.1"]
                 [metrics-clojure-ring "1.0.1"]
                 [me.raynes/fs "1.4.6"]
                 [com.taoensso/timbre "3.4.0"]
                 [com.cemerick/url "0.1.1"]
                 [irclj "0.5.0-alpha2"]
                 [clojurewerkz/quartzite "1.0.1"]
                 [congomongo "0.5.0"]
                 [enlive "1.1.1"]
                 [org.apache.jena/jena-core "2.10.1"]
                 [xom "1.2.5"]
                 [clj-time "0.14.0"]
                 [clj-http "3.7.0"]
                 [org.clojure/core.incubator "0.1.2"]
                 [org.clojure/data.json "0.2.0"]
                 [org.clojure/data.xml "0.0.7"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.clojure/core.memoize "0.5.8"]
                 [org.clojure/math.combinatorics "0.0.4"]
                 [com.novemberain/langohr "1.4.1"]
                 [liberator "0.15.2"]
                 [compojure "1.6.0"]
                 [ring "1.3.0"]
                 [metosin/ring-swagger "0.26.0"]
                 [metosin/ring-swagger-ui "3.9.0"]
                 [ring-basic-authentication "1.0.5"]
                 [ring-basic-authentication "1.0.5"]
                 [http-kit "2.2.0"]
                 [instaparse "1.4.1"]
                 [com.github.kyleburton/clj-xpath "1.4.3"]
                 [kjw/ring-logstash "0.1.3"]
                 [crossref/heartbeat "0.1.4"]
                 [robert/bruce "0.7.1"]
                 [bigml/sampling "3.0"]
                 [digest "1.4.4"]
                 [cc.qbits/spandex "0.5.2"]
                 [dk.ative/docjure "1.11.0"]
                 [environ "1.0.3"]
                 [javax.xml.bind/jaxb-api "2.3.1"]])


