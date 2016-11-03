(ns user
  (:require [clojure.tools.namespace.repl :refer [clear refresh-all]]))

(defn refresh []
  (clojure.tools.namespace.repl/refresh))

(defn load-dev []
  (require 'dev)
  (in-ns 'dev))

(def dev load-dev)
