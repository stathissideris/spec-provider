(defproject spec-provider "0.4.10-cljc"
  :description "Infer clojure specs from sample data. Inspired by F#'s type providers."
  :url "https://github.com/stathissideris/spec-provider"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0-beta2"]
                 [pretty-spec "0.1.3"]
                 [org.clojure/clojurescript "1.9.908"]]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/test.check "0.9.0"]
                                  [lein-doo "0.1.7"]]}}

  :plugins [[lein-cljsbuild "1.1.6"]
            [lein-doo "0.1.7"]]

  :cljsbuild {:builds [{:id "test-build"
                        :source-paths ["src" "target/classes" "test"]
                        :compiler {:output-to "out/testable.js"
                                   :main spec-provider.cljs-test-runner
                                   :target :nodejs
                                   :optimizations :none}}]}
  )
