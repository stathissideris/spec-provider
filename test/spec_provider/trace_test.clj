(ns spec-provider.trace-test
  (:refer-clojure :exclude [float?])
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

(deftest set-names-test
  (is
   (= `(s/cat
        :a integer?
        :b integer?
        :c integer?
        :d integer?
        :rest (s/spec (s/cat :el0 integer? :el1 string?)))
      (set-names `(s/cat
                   :el0 integer?
                   :el1 integer?
                   :el2 integer?
                   :el3 integer?
                   :el4 (s/spec (s/cat :el0 integer? :el1 string?)))
                 '{:args
                   [[:simple a]
                    [:simple b]
                    [:simple c]
                    [:simple d]]
                   :var-args {:amp & :arg [:simple rest]}})))
  (is
   (= `(s/cat
        :a integer?
        :b integer?
        :c integer?
        :d integer?
        :rest (s/spec (s/cat :el0 integer? :el1 string?)))
      (set-names `(s/cat
                   :el0 integer?
                   :el1 integer?
                   :el2 integer?
                   :el3 integer?
                   :el4 (s/spec (s/cat :el0 integer? :el1 string?)))
                 (s/conform ::sut/args '[a b c d & rest]))))
  (is
   (= `(s/cat
        :a integer?
        :b integer?
        :outer (s/spec
                (s/cat
                 :inner (s/spec (s/cat :v1 integer? :v2 integer?))
                 :v3 integer?))
        :c integer?
        :d integer?
        :el5 (s/keys :req-un [::bar ::foo])
        :bazz (s/and empty? map?))
      (set-names `(s/cat
                   :el0 integer?
                   :el1 integer?
                   :el2 (s/spec
                         (s/cat
                          :el0 (s/spec (s/cat :el0 integer? :el1 integer?))
                          :el1 integer?))
                   :el3 integer?
                   :el4 integer?
                   :el5 (s/keys :req-un [::bar ::foo])
                   :el6 (s/and empty? map?))
                 (s/conform ::sut/args '[a b [[v1 v2 :as inner] v3 :as outer] c d {:keys [foo bar]} {:keys [baz], :as bazz}])))))

(deftest instrument-test
  (testing "test 0"
    (instrument (defn foo0 [] "lol"))
    (foo0)
    (is
     (= [`(s/fdef foo0 :args (s/cat ) :ret string?)]
        (fn-specs 'spec-provider.trace-test/foo0))))
  ;;(pprint-fn-specs 'spec-provider.trace-test/foo0 'spec-provider.trace-test 's)

  (testing "test 0-1"
    (instrument (defn foo0-1 [] {:bar "test"}))
    (foo0-1)
    (is
     (= [`(s/def ::bar string?)
         `(s/fdef foo0-1 :args (s/cat) :ret (s/keys :req-un [::bar]))]
        (fn-specs 'spec-provider.trace-test/foo0-1))))
  ;;(pprint-fn-specs 'spec-provider.trace-test/foo0-1 'spec-provider.trace-test 's)

  (testing "test 1"
   (instrument
    (defn foo1 "doc"
      ([a b c d e f g h i j & rest]
       (swap! (atom []) conj 1)
       (swap! (atom []) conj 2)
       ;;{:result (* d (+ a b c))}
       6)
      ([a b [[v1 v2] v3] c d {:keys [foo bar]} {:keys [baz] :as bazz}]
       (swap! (atom []) conj 1)
       (swap! (atom []) conj 2)
       ;;{:result (* d (+ a b c))}
       {:bar 1M})))

   (do
     (foo1 10 20 30 40 50 60 70 80 90 100 110 "string")
     (foo1 10 20 30 40 50 60 70 80 90 100 110 "string" :kkk)
     (foo1 10 20 30 40 50 60 70 80 90 100 110 "string" {:bar :kkk})
     (foo1 10 20 30 40 50 60 70 80 90 100)
     (foo1 1 2 [[3 4] 5] 6 7 {:foo 8 :bar 9} {})
     (foo1 1 2 [[3 4] 5] 6 7 {:foo 8 :bar 9} {:bar "also string"}))

   (pprint-fn-specs 'spec-provider.trace-test/foo1 'spec-provider.trace-test 's))

  (testing "test 2"
   (instrument
    (defn foo2 "doc"
      [a b c d & rest]
      (swap! (atom []) conj 1)
      (swap! (atom []) conj 2)
      ;;{:result (* d (+ a b c))}
      6))

   (do
     (foo2 10 20 30 40 "string")
     (foo2 10 20 30 40 "string" [1 2 3 "foo"]))

   (is
    (= `[(s/fdef
           foo2
           :args
           (s/cat
            :a integer?
            :b integer?
            :c integer?
            :d integer?
            :rest (s/cat
                   :el0 (s/? string?)
                   :el1 (s/? (s/spec
                              (s/cat
                               :el0 integer?
                               :el1 integer?
                               :el2 integer?
                               :el3 string?)))))
           :ret
           integer?)]
       (fn-specs 'spec-provider.trace-test/foo2)))

   ;;(pprint-fn-specs 'spec-provider.trace-test/foo2 'spec-provider.trace-test 's)
   ))
