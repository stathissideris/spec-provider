(ns spec-provider.stats-test
  (:require [spec-provider.stats :refer :all]
            [clojure.test :refer :all]
            [spec-provider.person-spec :as person]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

(deftest collect-stats-test
  (is (collect-stats (gen/sample (s/gen integer?) 1000)))
  (is (collect-stats (gen/sample (s/gen (s/coll-of integer?)) 1000)))
  (is (collect-stats (gen/sample (s/gen ::person/person) 100))))
