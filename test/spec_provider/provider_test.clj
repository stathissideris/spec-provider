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
               :el2 (clojure.spec/keys :req-un [:foo/foo :foo/bar])))))
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
             (clojure.spec/keys :req-un [:foo/foo :foo/bar])))
         (infer-specs [{:foo 1 :bar {:baz 2 :boo 3}}] :foo/map)))
  (is (infer-specs (gen/sample (s/gen integer?) 1000) :foo/int))
  (is (infer-specs (gen/sample (s/gen (s/coll-of integer?)) 1000) :foo/coll-of-ints))
  (is (infer-specs (gen/sample (s/gen ::person/person) 100) :foo/person)))

(deftest pprint-specs-test
  (let [specs '[(clojure.spec/def :person/id (clojure.spec/or :numeric pos-int? :string string?))
                (clojure.spec/def :person/codes (clojure.spec/coll-of keyword? :max-gen 5))
                (clojure.spec/def :person/first-name string?)
                (clojure.spec/def :person/surname string?)
                (clojure.spec/def :person/k keyword?)]]
    (with-out-str
      (pprint-specs specs 'person 's))))
