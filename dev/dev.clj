(ns dev
  (:require [clojure.test :as test]
            [clojure.spec.test.alpha :as stest]))

(defn instrument-all []
  (stest/instrument (stest/instrumentable-syms)))

(defn unstrument-all []
  (stest/unstrument (stest/instrumentable-syms)))

(defn run-all-my-tests []
  (instrument-all)
  (test/run-all-tests #"^spec-provider.+$"))
