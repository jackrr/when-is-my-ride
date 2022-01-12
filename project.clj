(defproject when-is-my-ride "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[manifold "0.2.3"]
                 [metosin/malli "0.7.5"]
                 [metosin/muuntaja "0.6.8"]
                 [metosin/reitit "0.5.15"]
                 [org.clojure/clojure "1.10.3"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [teknql/systemic "0.2.1"]
                 [wing/repl "0.1.4"]]
  :main ^:skip-aot when-is-my-ride.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
