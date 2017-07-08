(ns spec-provider.trace-test
  (:require [spec-provider.trace :as trace]
            [clojure.test :refer [deftest testing is]]))

(deftest record-args-test
  (let [a (atom {})]
    (trace/record-args! "fun" a '[a b c])
    (trace/record-args! "fun" a '[a b & rest])
    (is (= {"fun" {:arg-names {3         '[a b c]
                               :var-args '[a b & rest]}}} @a))))
