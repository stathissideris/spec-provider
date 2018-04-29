(defproject spec-provider "0.4.12"
  :description "Infer clojure specs from sample data. Inspired by F#'s type providers."
  :url "https://github.com/stathissideris/spec-provider"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-beta2"]
                 [pretty-spec "0.1.3"]
                 [org.clojure/clojurescript "1.10.238"]]

  :source-paths ["src/clj"
                 "src/cljc"]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/test.check "0.9.0"]
                                  [lein-doo "0.1.7"]
                                  [pjstadig/humane-test-output "0.8.3"]]}}

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.10"]]

  :doo {:build "test-build"
        :alias {:default [:node]}}

  :cljsbuild {:builds [{:id "test-build"
                        :source-paths ["src/cljc"
                                       "test/cljc"
                                       "target/classes"]
                        :compiler {:output-to "out/testable.js"
                                   :main spec-provider.cljs-test-runner
                                   :target :nodejs
                                   :optimizations :none}}]})
