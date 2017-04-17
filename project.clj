(defproject spec-provider "0.3.1"
  :description "The equivalent of F#'s type providers but with clojure.spec"
  :url "https://github.com/stathissideris/spec-provider"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/test.check "0.9.0"]]}})
