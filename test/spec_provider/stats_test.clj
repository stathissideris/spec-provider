(ns spec-provider.stats-test
  (:require [spec-provider.stats :refer :all :as stats]
            [clojure.test :refer :all]
            [spec-provider.person-spec :as person]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

(deftest collect-stats-test
  (testing "collect vector stats"
   (is (= #::stats
          {:sample-count 1
           :pred-map
           {sequential?
            #::stats
            {:sample-count 1
             :min-length 3
             :max-length 3}}
           :distinct-values #{}
           :elements-coll
           #::stats
           {:sample-count 3
            :pred-map
            {integer?
             #::stats
             {:sample-count 3
              :min 1
              :max 2}}
            :distinct-values #{1 2}}}
          (collect-stats [[1 2 2]]))))

  (testing "collect positional vector stats"
   (is (= #::stats
          {:distinct-values #{}
           :sample-count 1
           :pred-map
           {sequential?
            #::stats
            {:sample-count 1
             :min-length 3
             :max-length 3}}
           :elements-pos
           {0
            #::stats
            {:distinct-values #{1}
             :sample-count 1
             :pred-map
             {integer?
              #::stats
              {:sample-count 1
               :min 1
               :max 1}}}
            1
            #::stats
            {:distinct-values #{2}
             :sample-count 1
             :pred-map
             {integer?
              #::stats
              {:sample-count 1
               :min 2
               :max 2}}}
            2
            #::stats
            {:distinct-values #{2}
             :sample-count 1
             :pred-map
             {integer?
              #::stats
              {:sample-count 1
               :min 2
               :max 2}}}}}
          (collect-stats [[1 2 2]] {::stats/positional true}))))

  (testing "positional stats are collected differently to normal stats"
    (is (not= (collect-stats [[1 2 2]])
              (collect-stats [[1 2 2]] {::stats/positional true}))))

  (is (collect-stats (gen/sample (s/gen integer?) 1000)))
  (is (collect-stats (gen/sample (s/gen (s/coll-of integer?)) 1000)))
  (is (collect-stats (gen/sample (s/gen ::person/person) 100))))
