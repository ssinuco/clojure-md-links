(defproject mdlinks "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"] [org.commonmark/commonmark "0.18.1"] [clj-http "3.12.3"]]
  :main ^:skip-aot mdlinks.core
  :target-path "target/%s"
  :aot [mdlinks.visitors.LinkVisitor]
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
