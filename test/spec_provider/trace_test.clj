(ns spec-provider.trace-test
  (:require [spec-provider.trace :refer :all :as sut]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]))

(deftest record-args-test
  (let [a (atom {})]
    (record-args! "fun" a '[a b c])
    (record-args! "fun" a '[a b & rest])
    (is (= {"fun" {:arg-names {3         '[a b c]
                               :var-args '[a b & rest]}}} @a))))

(deftest parse-args-test
  (is (= {:args [[:simple 'a] [:simple 'b] [:simple 'c]]}
         (s/conform ::sut/args '[a b c])))
  (is (= {:args     [[:simple 'a] [:simple 'b] [:simple 'c]]
          :var-args {:amp '& :arg [:simple 'z]}}
         (s/conform ::sut/args '[a b c & z])))
  (is (= {:args '[[:simple a]
                  [:simple b]
                  [:vector-destr
                   {:args [[:vector-destr {:args [[:simple v1] [:simple v2]]}]
                           [:simple v3]]
                    :varargs {:amp & :arg [:simple rest]}}]
                  [:simple c]
                  [:simple d]
                  [:map-destr [:keys-destr {:keys [foo bar]}]]
                  [:simple e]
                  [:simple f]
                  [:map-destr [:keys-destr {:keys [baz] :as bazz}]]]}
         (s/conform ::sut/args
                    '[a b [[v1 v2] v3 & rest]
                      c
                      d
                      {:keys [foo bar]}
                      e
                      f
                      {:keys [baz] :as bazz}]))))
