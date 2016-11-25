(ns spec-provider.trace-test
  (:require [spec-provider.trace :refer :all]
            [clojure.test :refer :all]))

(deftest record-args-test
  (let [a (atom {})]
    (record-args! "fun" a '[a b c])
    (record-args! "fun" a '[a b & rest])
    (is (= {"fun" {:arg-names {3         '[a b c]
                               :var-args '[a b & rest]}}} @a))))
