(ns spec-provider.provider-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [spec-provider.provider :refer :all]
            [spec-provider.person-spec :as person]
            [spec-provider.stats :as stats]))

(deftest summarize-stats-test
  (is (= '((clojure.spec.alpha/def :domain/foo integer?))
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

  (is (= '((clojure.spec.alpha/def :foo/bar integer?)
           (clojure.spec.alpha/def :foo/foo integer?)
           (clojure.spec.alpha/def
             :foo/stuff
             (clojure.spec.alpha/or
              :map (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])
              :simple integer?)))
         (infer-specs [1 2 {:foo 3 :bar 4}] :foo/stuff)))

  (is (= '((clojure.spec.alpha/def :foo/vector
             (clojure.spec.alpha/coll-of (clojure.spec.alpha/or :integer integer? :map map?))))
         (infer-specs [[1 2 {:foo 3 :bar 4}]] :foo/vector)))

  (is (= '((clojure.spec.alpha/def :foo/boo integer?)
           (clojure.spec.alpha/def :foo/baz integer?)
           (clojure.spec.alpha/def
             :foo/bar
             (clojure.spec.alpha/keys :req-un [:foo/baz :foo/boo]))
           (clojure.spec.alpha/def :foo/foo integer?)
           (clojure.spec.alpha/def
             :foo/map
             (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])))
         (infer-specs [{:foo 1 :bar {:baz 2 :boo 3}}] :foo/map)))

  (is (infer-specs [{:a {:b [1 2]}} {:b [1]}] ::foo)) ;; issue #7
  (is (infer-specs [{:a {:b [{} {}]}} {:b [{}]}] ::foo)) ;; issue #7

  (is (infer-specs (gen/sample (s/gen integer?) 1000) :foo/int))

  (is (infer-specs (gen/sample (s/gen (s/coll-of integer?)) 1000) :foo/coll-of-ints))

  (testing "positional (cat) specs"
    (is (= '((clojure.spec.alpha/def :foo/bar integer?)
             (clojure.spec.alpha/def :foo/foo integer?)
             (clojure.spec.alpha/def :foo/vector
               (clojure.spec.alpha/cat
                :el0 integer?
                :el1 integer?
                :el2 (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo]))))
           (infer-specs [[1 2 {:foo 3 :bar 4}]]
                        :foo/vector
                        #::stats{:options #::stats{:positional true}})))
    (is (= '((clojure.spec.alpha/def :foo/bar integer?)
             (clojure.spec.alpha/def :foo/foo integer?)
             (clojure.spec.alpha/def
               :foo/vector
               (clojure.spec.alpha/cat
                :el0 integer?
                :el1 integer?
                :el2 (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])
                :el3 (clojure.spec.alpha/spec
                      (clojure.spec.alpha/cat
                       :el0 double?
                       :el1 double?
                       :el2 double?)))))
           (infer-specs [[1 2 {:foo 3 :bar 4} [1.2 5.4 3.0]]]
                        :foo/vector
                        #::stats{:options #::stats{:positional true}}))))

  (testing "order of or"
    (is (= '((clojure.spec.alpha/def
               :spec-provider.provider-test/stuff
               (clojure.spec.alpha/or
                :boolean boolean?
                :double double?
                :float float?
                :integer integer?
                :keyword keyword?
                :map map?
                :set set?)))
           (infer-specs [:k true (double 1) false (float 3) #{} {} (int 5)] ::stuff))))

  (testing "issue #1 - coll-of overrides everything"
    (is (= '((clojure.spec.alpha/def
               :spec-provider.provider-test/stuff
               (clojure.spec.alpha/or
                :collection
                (clojure.spec.alpha/coll-of integer?)
                :simple
                (clojure.spec.alpha/or
                 :boolean boolean?
                 :double double?
                 :float float?
                 :integer integer?
                 :keyword keyword?
                 :map map?
                 :set set?))))
           (infer-specs [:k true (double 1) false '(1 2 3) (float 3) #{} {} (int 5)] ::stuff))))

  (testing "nilable"
    (is (= '((clojure.spec.alpha/def ::a
               (clojure.spec.alpha/nilable integer?))
             (clojure.spec.alpha/def ::foo
               (clojure.spec.alpha/keys :req-un [::a])))
           (infer-specs [{:a 5} {:a nil}] ::foo)))
    (is (= '((clojure.spec.alpha/def ::foo (clojure.spec.alpha/nilable integer?)))
           (infer-specs [1 2 nil] ::foo)))
    (is (= '((clojure.spec.alpha/def ::foo (clojure.spec.alpha/coll-of (clojure.spec.alpha/nilable integer?))))
           (infer-specs [[1] [2] [nil]] ::foo)))
    (is (= '((clojure.spec.alpha/def ::foo (clojure.spec.alpha/nilable (clojure.spec.alpha/coll-of integer?))))
           (infer-specs [[1] [2] nil] ::foo)))
    (is (= '((clojure.spec.alpha/def ::a integer?)
             (clojure.spec.alpha/def
               ::foo
               (clojure.spec.alpha/nilable (clojure.spec.alpha/keys :req [::a]))))
           (infer-specs [{::a 9} {::a 10} nil] ::foo)))
    (testing " with positional"
      (is (= '((clojure.spec.alpha/def :foo/bar integer?)
               (clojure.spec.alpha/def :foo/foo integer?)
               (clojure.spec.alpha/def
                 :foo/vector
                 (clojure.spec.alpha/cat
                  :el0 (clojure.spec.alpha/nilable integer?)
                  :el1 integer?
                  :el2 (clojure.spec.alpha/nilable (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])))))
             (infer-specs [[1 2 {:foo 3 :bar 4}]
                           [nil 2 {:foo 3 :bar 4}]
                           [1 2 nil]]
                          :foo/vector
                          #::stats{:options #::stats{:positional true}}))))))

(deftest person-spec-inference-test
  (let [persons (gen/sample (s/gen ::person/person) 100)]
    (is (= (into
            #{}
            '((clojure.spec.alpha/def :person/codes (clojure.spec.alpha/coll-of keyword?))
              (clojure.spec.alpha/def :person/phone-number string?)
              (clojure.spec.alpha/def :person/street-number integer?)
              (clojure.spec.alpha/def :person/country string?)
              (clojure.spec.alpha/def :person/city string?)
              (clojure.spec.alpha/def :person/street string?)
              (clojure.spec.alpha/def
                :person/address
                (clojure.spec.alpha/keys :req-un [:person/city :person/country :person/street] :opt-un [:person/street-number]))
              (clojure.spec.alpha/def :person/age integer?)
              (clojure.spec.alpha/def :person/k (clojure.spec.alpha/nilable keyword?))
              (clojure.spec.alpha/def :person/surname string?)
              (clojure.spec.alpha/def :person/first-name string?)
              (clojure.spec.alpha/def :person/id (clojure.spec.alpha/or :integer integer? :string string?))
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
            '((clojure.spec.alpha/def :person/codes (clojure.spec.alpha/coll-of keyword?))
              (clojure.spec.alpha/def :person/phone-number string?)
              (clojure.spec.alpha/def :person/id (clojure.spec.alpha/or :integer integer? :keyword keyword? :string string?))
              (clojure.spec.alpha/def :person/street-number integer?)
              (clojure.spec.alpha/def :person/country string?)
              (clojure.spec.alpha/def :person/city string?)
              (clojure.spec.alpha/def :person/street string?)
              (clojure.spec.alpha/def
                :person/address
                (clojure.spec.alpha/keys
                 :req-un [:person/city :person/country :person/id :person/street]
                 :opt-un [:person/street-number]))
              (clojure.spec.alpha/def :person/age integer?)
              (clojure.spec.alpha/def :person/k (clojure.spec.alpha/nilable keyword?))
              (clojure.spec.alpha/def :person/surname string?)
              (clojure.spec.alpha/def :person/first-name string?)
              (clojure.spec.alpha/def :person/role #{:programmer :designer})
              (clojure.spec.alpha/def
                :person/person
                (clojure.spec.alpha/keys
                 :req [:person/role]
                 :req-un [:person/address :person/age :person/first-name :person/id :person/k :person/surname]
                 :opt-un [:person/codes :person/phone-number]))))
           (set (infer-specs persons :person/person))))))

(deftest pprint-specs-test
  (let [specs '[(clojure.spec.alpha/def :person/id (clojure.spec.alpha/or :numeric pos-int? :string string?))
                (clojure.spec.alpha/def :person/codes (clojure.spec.alpha/coll-of keyword? :max-gen 5))
                (clojure.spec.alpha/def :person/first-name string?)
                (clojure.spec.alpha/def :person/surname string?)
                (clojure.spec.alpha/def :person/k keyword?)]]
    (is (= (str "(s/def ::id (s/or :numeric pos-int? :string string?))\n"
                "(s/def ::codes (s/coll-of keyword? :max-gen 5))\n"
                "(s/def ::first-name string?)\n"
                "(s/def ::surname string?)\n"
                "(s/def ::k keyword?)\n")
           (with-out-str
             (pprint-specs specs 'person 's))))))
