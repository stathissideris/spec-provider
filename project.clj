(defproject spec-provider "0.4.14"
  :description "Infer clojure specs from sample data. Inspired by F#'s type providers."
  :url "https://github.com/stathissideris/spec-provider"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [pretty-spec "0.1.3"]
                 [org.clojure/clojurescript "1.10.238"]]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/test.check "0.10.0-alpha2"]
                                  [lein-doo "0.1.10"]
                                  [pjstadig/humane-test-output "0.8.3"]]
                   :injections   [(require 'spec-provider.provider) ;; loads code to instrument
                                  (require 'clojure.spec.test.alpha)
                                  (clojure.spec.test.alpha/instrument)
                                  (.println System/err "Instrumented specs")]}}

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.10"]
            [lein-tach "1.0.0"]]

  :doo {:build "test"
        :alias {:default [:node]}}

  :tach {:test-runner-ns spec-provider.cljs-self-test-runner}

  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-to "out/testable.js"
                                   :main spec-provider.cljs-test-runner
                                   :target :nodejs
                                   :optimizations :none}}]})
