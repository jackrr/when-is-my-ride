(defproject when-is-my-ride "0.0.0"
  :description "Clojure interface to GTFS transit data"
  :url "https://whenismyride.com"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[com.google.protobuf/protobuf-java "3.8.0"]
                 [com.taoensso/tufte "2.2.0"]
                 [datascript "1.3.5"]
                 [hato "0.8.2"]
                 [manifold "0.2.3"]
                 [metosin/malli "0.7.5"]
                 [metosin/muuntaja "0.6.8"]
                 [metosin/reitit "0.5.15"]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/data.csv "1.0.0"]
                 [ring-cors "0.1.13"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [teknql/systemic "0.2.1"]
                 [wing/repl "0.1.4"]]
  :main ^:skip-aot when-is-my-ride.core
  :target-path "target/%s"
  :java-source-paths ["protoc/"]
  :profiles {:dev {:resource-paths ["secrets"]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
