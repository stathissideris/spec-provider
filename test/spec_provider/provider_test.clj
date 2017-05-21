(ns spec-provider.provider-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [spec-provider.provider :refer :all]
            [spec-provider.person-spec :as person]
            [spec-provider.stats :as stats]))

(deftest summarize-stats-test
  (is (= '((clojure.spec.alpha/def :domain/foo clojure.core/integer?))
         (summarize-stats
          #::stats
          {:sample-count 1
           :pred-map {integer?
                      #::stats
                      {:sample-count 1
                       :min 6
                       :max 6}}
           :distinct-values #{6}}
          'domain/foo))))

(deftest infer-specs-test

  (is (= '((clojure.spec.alpha/def :foo/bar clojure.core/integer?)
           (clojure.spec.alpha/def :foo/foo clojure.core/integer?)
           (clojure.spec.alpha/def
             :foo/stuff
             (clojure.spec.alpha/or
              :map (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])
              :simple clojure.core/integer?)))
         (infer-specs [1 2 {:foo 3 :bar 4}] :foo/stuff)))

  (is (= '((clojure.spec.alpha/def :foo/bar clojure.core/integer?)
           (clojure.spec.alpha/def :foo/foo clojure.core/integer?)
           (clojure.spec.alpha/def
             :foo/vector
             (clojure.spec.alpha/coll-of
              (clojure.spec.alpha/or
               :map
               (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])
               :simple
               clojure.core/integer?))))
         (infer-specs [[1 2 {:foo 3 :bar 4}]] :foo/vector)))

  (is (= '((clojure.spec.alpha/def :foo/boo clojure.core/integer?)
           (clojure.spec.alpha/def :foo/baz clojure.core/integer?)
           (clojure.spec.alpha/def
             :foo/bar
             (clojure.spec.alpha/keys :req-un [:foo/baz :foo/boo]))
           (clojure.spec.alpha/def :foo/foo clojure.core/integer?)
           (clojure.spec.alpha/def
             :foo/map
             (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])))
         (infer-specs [{:foo 1 :bar {:baz 2 :boo 3}}] :foo/map)))

  (is (infer-specs [{:a {:b [1 2]}} {:b [1]}] ::foo)) ;; issue #7
  (is (infer-specs [{:a {:b [{} {}]}} {:b [{}]}] ::foo)) ;; issue #7
  (is (infer-specs [{:a {:b [[] []]}} {:b [[]]}] ::foo))

  (is (infer-specs (gen/sample (s/gen integer?) 1000) :foo/int))

  (is (infer-specs (gen/sample (s/gen (s/coll-of integer?)) 1000) :foo/coll-of-ints))

  (testing "positional (cat) specs"
    (is (= '((clojure.spec.alpha/def :foo/bar clojure.core/integer?)
             (clojure.spec.alpha/def :foo/foo clojure.core/integer?)
             (clojure.spec.alpha/def :foo/vector
               (clojure.spec.alpha/cat
                :el0 clojure.core/integer?
                :el1 clojure.core/integer?
                :el2 (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo]))))
           (infer-specs [[1 2 {:foo 3 :bar 4}]]
                        :foo/vector
                        #::stats{:options #::stats{:positional true}})))
    (is (= '((clojure.spec.alpha/def :foo/bar clojure.core/integer?)
             (clojure.spec.alpha/def :foo/foo clojure.core/integer?)
             (clojure.spec.alpha/def
               :foo/vector
               (clojure.spec.alpha/cat
                :el0 clojure.core/integer?
                :el1 clojure.core/integer?
                :el2 (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])
                :el3 (clojure.spec.alpha/spec
                      (clojure.spec.alpha/cat
                       :el0 clojure.core/double?
                       :el1 clojure.core/double?
                       :el2 clojure.core/double?)))))
           (infer-specs [[1 2 {:foo 3 :bar 4} [1.2 5.4 3.0]]]
                        :foo/vector
                        #::stats{:options #::stats{:positional true}}))))

  (testing "order of or"
    (is (= '((clojure.spec.alpha/def
               :spec-provider.provider-test/stuff
               (clojure.spec.alpha/or
                :map clojure.core/map?
                :set (clojure.spec.alpha/coll-of clojure.core/any? :kind clojure.core/set?)
                :simple
                (clojure.spec.alpha/or
                 :boolean clojure.core/boolean?
                 :double  clojure.core/double?
                 :float   clojure.core/float?
                 :integer clojure.core/integer?
                 :keyword clojure.core/keyword?))))
           (infer-specs [:k true (double 1) false (float 3) #{} {} (int 5)] ::stuff))))

  (testing "issue #1 - coll-of overrides everything"
    (is (= '((clojure.spec.alpha/def
               :spec-provider.provider-test/stuff
               (clojure.spec.alpha/or
                :collection (clojure.spec.alpha/coll-of clojure.core/integer?)
                :map clojure.core/map?
                :set (clojure.spec.alpha/coll-of clojure.core/any? :kind clojure.core/set?)
                :simple
                (clojure.spec.alpha/or
                 :boolean clojure.core/boolean?
                 :double  clojure.core/double?
                 :float   clojure.core/float?
                 :integer clojure.core/integer?
                 :keyword clojure.core/keyword?))))
           (infer-specs [:k true (double 1) false '(1 2 3) (float 3) #{} {} (int 5)] ::stuff))))

  (testing "nilable"
    (is (= '((clojure.spec.alpha/def ::a
               (clojure.spec.alpha/nilable clojure.core/integer?))
             (clojure.spec.alpha/def ::foo
               (clojure.spec.alpha/keys :req-un [::a])))
           (infer-specs [{:a 5} {:a nil}] ::foo)))
    (is (= '((clojure.spec.alpha/def ::foo (clojure.spec.alpha/nilable clojure.core/integer?)))
           (infer-specs [1 2 nil] ::foo)))
    (is (= '((clojure.spec.alpha/def ::foo (clojure.spec.alpha/coll-of (clojure.spec.alpha/nilable clojure.core/integer?))))
           (infer-specs [[1] [2] [nil]] ::foo)))
    (is (= '((clojure.spec.alpha/def ::foo (clojure.spec.alpha/nilable (clojure.spec.alpha/coll-of clojure.core/integer?))))
           (infer-specs [[1] [2] nil] ::foo)))
    (is (= '((clojure.spec.alpha/def ::a clojure.core/integer?)
             (clojure.spec.alpha/def
               ::foo
               (clojure.spec.alpha/nilable (clojure.spec.alpha/keys :req [::a]))))
           (infer-specs [{::a 9} {::a 10} nil] ::foo)))
    (testing " with positional"
      (is (= '((clojure.spec.alpha/def :foo/bar clojure.core/integer?)
               (clojure.spec.alpha/def :foo/foo clojure.core/integer?)
               (clojure.spec.alpha/def
                 :foo/vector
                 (clojure.spec.alpha/cat
                  :el0 (clojure.spec.alpha/nilable clojure.core/integer?)
                  :el1 clojure.core/integer?
                  :el2 (clojure.spec.alpha/nilable (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])))))
             (infer-specs [[1 2 {:foo 3 :bar 4}]
                           [nil 2 {:foo 3 :bar 4}]
                           [1 2 nil]]
                          :foo/vector
                          #::stats{:options #::stats{:positional true}})))))

  (testing "do I know you from somewhere?"
    (is (= '((clojure.spec.alpha/def :foo/zz clojure.core/integer?)
             (clojure.spec.alpha/def :foo/b
               (clojure.spec.alpha/nilable (clojure.spec.alpha/keys :req-un [:foo/zz])))
             (clojure.spec.alpha/def :foo/a (clojure.spec.alpha/coll-of :foo/b))
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :req-un [:foo/a :foo/b])))
           (infer-specs [{:a [{:zz 1}] :b {:zz 2}}
                         {:a [{:zz 1} {:zz 4} nil] :b nil}] :foo/stuff))))

  (testing "sets"
    (is (= '((clojure.spec.alpha/def :foo/a (clojure.spec.alpha/coll-of clojure.core/keyword? :kind clojure.core/set?))
             (clojure.spec.alpha/def :foo/b (clojure.spec.alpha/coll-of clojure.core/keyword? :kind clojure.core/set?))
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :req-un [:foo/b] :opt-un [:foo/a])))
           (infer-specs
            [{:b #{:a}}
             {:b #{:b}}
             {:a #{:c}}]
            :foo/stuff))))

  (testing "rewrite/all-nilable-or"
    (is (= '((clojure.spec.alpha/def
               :foo/stuff
               (clojure.spec.alpha/nilable
                (clojure.spec.alpha/or
                 :integer clojure.core/integer?
                 :keyword clojure.core/keyword?
                 :string  clojure.core/string?))))
           (infer-specs [1 :a "stirng" nil] :foo/stuff)))))

(deftest person-spec-inference-test
  (let [persons (gen/sample (s/gen ::person/person) 100)]
    (is (= (into
            #{}
            '((clojure.spec.alpha/def :person/codes (clojure.spec.alpha/coll-of clojure.core/keyword?))
              (clojure.spec.alpha/def :person/phone-number clojure.core/string?)
              (clojure.spec.alpha/def :person/street-number clojure.core/integer?)
              (clojure.spec.alpha/def :person/country clojure.core/string?)
              (clojure.spec.alpha/def :person/city clojure.core/string?)
              (clojure.spec.alpha/def :person/street clojure.core/string?)
              (clojure.spec.alpha/def
                :person/address
                (clojure.spec.alpha/keys :req-un [:person/city :person/country :person/street] :opt-un [:person/street-number]))
              (clojure.spec.alpha/def :person/age clojure.core/integer?)
              (clojure.spec.alpha/def :person/k (clojure.spec.alpha/nilable clojure.core/keyword?))
              (clojure.spec.alpha/def :person/surname clojure.core/string?)
              (clojure.spec.alpha/def :person/first-name clojure.core/string?)
              (clojure.spec.alpha/def :person/id (clojure.spec.alpha/or :integer clojure.core/integer? :string clojure.core/string?))
              (clojure.spec.alpha/def :person/role #{:programmer :designer})
              (clojure.spec.alpha/def
                :person/person
                (clojure.spec.alpha/keys
                 :req
                 [:person/role]
                 :req-un
                 [:person/address :person/age :person/first-name :person/id :person/k :person/surname]
                 :opt-un
                 [:person/codes :person/phone-number]))))
           (set (infer-specs persons :person/person))))))

(deftest person-spec-inference-with-merging-test
  (let [persons (map person/add-inconsistent-id
                     (gen/sample (s/gen ::person/person) 100))]
    (is (= (into
            #{}
            '((clojure.spec.alpha/def :person/codes (clojure.spec.alpha/coll-of clojure.core/keyword?))
              (clojure.spec.alpha/def :person/phone-number clojure.core/string?)
              (clojure.spec.alpha/def :person/id (clojure.spec.alpha/or :integer clojure.core/integer? :keyword clojure.core/keyword? :string clojure.core/string?))
              (clojure.spec.alpha/def :person/street-number clojure.core/integer?)
              (clojure.spec.alpha/def :person/country clojure.core/string?)
              (clojure.spec.alpha/def :person/city clojure.core/string?)
              (clojure.spec.alpha/def :person/street clojure.core/string?)
              (clojure.spec.alpha/def
                :person/address
                (clojure.spec.alpha/keys
                 :req-un [:person/city :person/country :person/id :person/street]
                 :opt-un [:person/street-number]))
              (clojure.spec.alpha/def :person/age clojure.core/integer?)
              (clojure.spec.alpha/def :person/k (clojure.spec.alpha/nilable clojure.core/keyword?))
              (clojure.spec.alpha/def :person/surname clojure.core/string?)
              (clojure.spec.alpha/def :person/first-name clojure.core/string?)
              (clojure.spec.alpha/def :person/role #{:programmer :designer})
              (clojure.spec.alpha/def
                :person/person
                (clojure.spec.alpha/keys
                 :req [:person/role]
                 :req-un [:person/address :person/age :person/first-name :person/id :person/k :person/surname]
                 :opt-un [:person/codes :person/phone-number]))))
           (set (infer-specs persons :person/person))))))

(deftest pprint-specs-test
  (let [specs '[(clojure.spec.alpha/def :person/id (clojure.spec.alpha/or :numeric pos-int? :string clojure.core/string?))
                (clojure.spec.alpha/def :person/codes (clojure.spec.alpha/coll-of clojure.core/keyword? :max-gen 5))
                (clojure.spec.alpha/def :person/first-name clojure.core/string?)
                (clojure.spec.alpha/def :person/surname clojure.core/string?)
                (clojure.spec.alpha/def :person/k clojure.core/keyword?)]]
    (is (= (str "(s/def ::id (s/or :numeric pos-int? :string string?))\n"
                "(s/def ::codes (s/coll-of keyword? :max-gen 5))\n"
                "(s/def ::first-name string?)\n"
                "(s/def ::surname string?)\n"
                "(s/def ::k keyword?)\n")
           (with-out-str
             (pprint-specs specs 'person 's))))))
