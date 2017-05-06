(ns spec-provider.provider-test
  (:require [spec-provider.provider :refer :all]
            [clojure.test :refer :all]
            [spec-provider.person-spec :as person]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [spec-provider.stats :as stats]))

(deftest summarize-stats-test
  (is (= '((clojure.spec/def :domain/foo integer?))
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
  (is (= '((clojure.spec/def :foo/vector
             (clojure.spec/coll-of (clojure.spec/or :integer integer? :map map?))))
         (infer-specs [[1 2 {:foo 3 :bar 4}]] :foo/vector)))
  (is (= '((clojure.spec/def :foo/bar integer?)
           (clojure.spec/def :foo/foo integer?)
           (clojure.spec/def :foo/vector
             (clojure.spec/spec
              (clojure.spec/cat
               :el0 integer?
               :el1 integer?
               :el2 (clojure.spec/keys :req-un [:foo/bar :foo/foo])))))
         (infer-specs [[1 2 {:foo 3 :bar 4}]]
                      :foo/vector
                      #::stats{:options #::stats{:positional true}})))
  (is (= '((clojure.spec/def :foo/boo integer?)
           (clojure.spec/def :foo/baz integer?)
           (clojure.spec/def
             :foo/bar
             (clojure.spec/keys :req-un [:foo/baz :foo/boo]))
           (clojure.spec/def :foo/foo integer?)
           (clojure.spec/def
             :foo/map
             (clojure.spec/keys :req-un [:foo/bar :foo/foo])))
         (infer-specs [{:foo 1 :bar {:baz 2 :boo 3}}] :foo/map)))
  (is (infer-specs (gen/sample (s/gen integer?) 1000) :foo/int))
  (is (infer-specs (gen/sample (s/gen (s/coll-of integer?)) 1000) :foo/coll-of-ints))
  (is (= '((clojure.spec/def
             :spec-provider.provider-test/stuff
             (clojure.spec/or
              :collection
              (clojure.spec/coll-of integer?)
              :simple
              (clojure.spec/or
               :boolean boolean?
               :double double?
               :float float?
               :integer integer?
               :keyword keyword?
               :map map?
               :set set?))))
         (infer-specs [:k true (double 1) false '(1 2 3) (float 3) #{} {} (int 5)] ::stuff)))
  (testing "order of or"
    (is (= '((clojure.spec/def
               :spec-provider.provider-test/stuff
               (clojure.spec/or
                :boolean boolean?
                :double double?
                :float float?
                :integer integer?
                :keyword keyword?
                :map map?
                :set set?)))
           (infer-specs [:k true (double 1) false (float 3) #{} {} (int 5)] ::stuff)))))

(deftest person-spec-inference-test
  (let [persons (gen/sample (s/gen ::person/person) 100)]
    (is (= (into
            #{}
            '((clojure.spec/def :person/codes (clojure.spec/coll-of keyword?))
              (clojure.spec/def :person/phone-number string?)
              (clojure.spec/def :person/street-number integer?)
              (clojure.spec/def :person/country string?)
              (clojure.spec/def :person/city string?)
              (clojure.spec/def :person/street string?)
              (clojure.spec/def
                :person/address
                (clojure.spec/keys :req-un [:person/city :person/country :person/street] :opt-un [:person/street-number]))
              (clojure.spec/def :person/age integer?)
              (clojure.spec/def :person/k keyword?)
              (clojure.spec/def :person/surname string?)
              (clojure.spec/def :person/first-name string?)
              (clojure.spec/def :person/id (clojure.spec/or :integer integer? :string string?))
              (clojure.spec/def :person/role #{:programmer :designer})
              (clojure.spec/def
                :person/person
                (clojure.spec/keys
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
            '((clojure.spec/def :person/codes (clojure.spec/coll-of keyword?))
              (clojure.spec/def :person/phone-number string?)
              (clojure.spec/def :person/id (clojure.spec/or :integer integer? :keyword keyword? :string string?))
              (clojure.spec/def :person/street-number integer?)
              (clojure.spec/def :person/country string?)
              (clojure.spec/def :person/city string?)
              (clojure.spec/def :person/street string?)
              (clojure.spec/def
                :person/address
                (clojure.spec/keys
                 :req-un [:person/city :person/country :person/id :person/street]
                 :opt-un [:person/street-number]))
              (clojure.spec/def :person/age integer?)
              (clojure.spec/def :person/k keyword?)
              (clojure.spec/def :person/surname string?)
              (clojure.spec/def :person/first-name string?)
              (clojure.spec/def :person/role #{:programmer :designer})
              (clojure.spec/def
                :person/person
                (clojure.spec/keys
                 :req [:person/role]
                 :req-un [:person/address :person/age :person/first-name :person/id :person/k :person/surname]
                 :opt-un [:person/codes :person/phone-number]))))
           (set (infer-specs persons :person/person))))))

(deftest pprint-specs-test
  (let [specs '[(clojure.spec/def :person/id (clojure.spec/or :numeric pos-int? :string string?))
                (clojure.spec/def :person/codes (clojure.spec/coll-of keyword? :max-gen 5))
                (clojure.spec/def :person/first-name string?)
                (clojure.spec/def :person/surname string?)
                (clojure.spec/def :person/k keyword?)]]
    (is (= (str "(s/def ::id (s/or :numeric pos-int? :string string?))\n"
                "(s/def ::codes (s/coll-of keyword? :max-gen 5))\n"
                "(s/def ::first-name string?)\n"
                "(s/def ::surname string?)\n"
                "(s/def ::k keyword?)\n")
           (with-out-str
             (pprint-specs specs 'person 's))))))
