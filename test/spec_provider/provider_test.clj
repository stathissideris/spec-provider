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
