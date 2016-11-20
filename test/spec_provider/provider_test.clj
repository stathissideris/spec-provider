(ns spec-provider.provider-test
  (:require [spec-provider.provider :refer :all]
            [clojure.test :refer :all]
            [spec-provider.person-spec :as person]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

(deftest infer-specs-test
  (is (infer-specs (gen/sample (s/gen integer?) 1000) :foo/int))
  (is (infer-specs (gen/sample (s/gen (s/coll-of integer?)) 1000) :foo/coll-of-ints))
  (is (infer-specs (gen/sample (s/gen ::person/person) 100) :foo/person)))

(deftest pprint-specs-test
  (let [specs '[(clojure.spec/def :person/id (clojure.spec/or :numeric pos-int? :string string?))
                (clojure.spec/def :person/codes (clojure.spec/coll-of keyword? :max-gen 5))
                (clojure.spec/def :person/first-name string?)
                (clojure.spec/def :person/surname string?)
                (clojure.spec/def :person/k keyword?)]]
    (pprint-specs specs 'person 's)))
