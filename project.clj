(defproject spec-provider "0.4.4"
  :description "Infer clojure specs from sample data. Inspired by F#'s type providers."
  :url "https://github.com/stathissideris/spec-provider"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha16"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/test.check "0.9.0"]]}})
