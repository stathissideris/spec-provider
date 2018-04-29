(ns spec-provider.stats-test
  (:refer-clojure :exclude [float?])
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [spec-provider.stats :as stats]
            [spec-provider.person-spec :as person]))

(deftest basic
  (is (stats/double? 2.3)))

(deftest collect-test
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
          (stats/collect [[1 2 2]]))))

  (testing "collect vector stats with double?"
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
            {stats/double?
             #::stats
             {:sample-count 3
              :min 1.5
              :max 2.4}}
            :distinct-values #{1.5 2.4}}}
          (stats/collect [[1.5 2.4 1.5]]))))

  #?(:clj
     (testing "collect vector stats with decimal?"
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
                {decimal?
                 #::stats
                 {:sample-count 3
                  :min 1M
                  :max 2M}}
                :distinct-values #{1M 2M}}}
              (stats/collect [[1M 2M 2M]])))))

  (testing "collect positional vector stats"
    (is (= #::stats
           {:distinct-values #{}
            :sample-count 1
            :pred-map
            {sequential? #::stats{:sample-count 1 :min-length 3 :max-length 3}}
            :elements-pos
            {0
             #::stats
             {:distinct-values #{1}
              :sample-count 1
              :pred-map
              {integer? #::stats{:sample-count 1 :min 1 :max 1}}}
             1
             #::stats
             {:distinct-values #{2}
              :sample-count 1
              :pred-map
              {integer? #::stats{:sample-count 1 :min 2 :max 2}}}
             2
             #::stats
             {:distinct-values #{2}
              :sample-count 1
              :pred-map
              {integer? #::stats{:sample-count 1 :min 2 :max 2}}}}}
           (stats/collect [[1 2 2]] {::stats/positional true}))))

  (testing "collect positional vector stats with doubles"
    (is (= #::stats
           {:distinct-values #{}
            :sample-count 1
            :pred-map
            {sequential? #::stats{:sample-count 1 :min-length 3 :max-length 3}}
            :elements-pos
            {0
             #::stats
             {:distinct-values #{1.5}
              :sample-count 1
              :pred-map
              {stats/double? #::stats{:sample-count 1 :min 1.5 :max 1.5}}}
             1
             #::stats
             {:distinct-values #{2.3}
              :sample-count 1
              :pred-map
              {stats/double? #::stats{:sample-count 1 :min 2.3 :max 2.3}}}
             2
             #::stats
             {:distinct-values #{2.2}
              :sample-count 1
              :pred-map
              {stats/double? #::stats{:sample-count 1 :min 2.2 :max 2.2}}}}}
           (stats/collect [[1.5 2.3 2.2]] {::stats/positional true}))))

  (testing "positional stats are collected differently to normal stats"
    (is (not= (stats/collect [[1 2 2]])
              (stats/collect [[1 2 2]] {::stats/positional true}))))

  (testing "stats for different types of maps"
    (is (= #::stats
           {:distinct-values #{},
            :sample-count 2,
            :pred-map
            {map?
             #::stats
             {:sample-count 2, :min-length 1, :max-length 1}},
            :map
            #::stats
            {:sample-count 2,
             :keyword-sample-count 2,
             :keys
             {:a
              #::stats
              {:distinct-values #{9},
               :sample-count 2,
               :pred-map
               {integer?
                #::stats
                {:sample-count 2,
                 :min 9,
                 :max 9}}}}}}
           (stats/collect [{:a 9} {:a 9}])))
    (is (= #::stats
           {:distinct-values #{},
            :sample-count 2,
            :pred-map
            {map? #::stats{:sample-count 2, :min-length 1, :max-length 1}},
            :map
            #::stats
            {:sample-count 2,
             :keyword-sample-count 1,
             :non-keyword-sample-count 1,
             :keys
             {:a
              #::stats
              {:distinct-values #{9},
               :sample-count 1,
               :pred-map
               {integer? #::stats{:sample-count 1,:min 9,:max 9}}},
              9
              #::stats
              {:distinct-values #{9},
               :sample-count 1,
               :pred-map
               {integer? #::stats{:sample-count 1,:min 9,:max 9}}}}}}
           (stats/collect [{:a 9} {9 9}])))
    (is (= #::stats
           {:distinct-values #{},
            :sample-count 4,
            :pred-map
            {map? #::stats{:sample-count 4, :min-length 0, :max-length 2}},
            :map
            #::stats
            {:sample-count 4,
             :keyword-sample-count 1,
             :keys
             {:a
              #::stats
              {:distinct-values #{9},
               :sample-count 2,
               :pred-map {integer? #::stats{:sample-count 2,:min 9,:max 9}}},
              9
              #::stats
              {:distinct-values #{9},
               :sample-count 2,
               :pred-map {integer? #::stats{:sample-count 2,:min 9,:max 9}}}},
             :non-keyword-sample-count 1,
             :mixed-sample-count 1,
             :empty-sample-count 1}}
           (stats/collect [{:a 9}
                           {9 9}
                           {:a 9 9 9}
                           {}]))))

  (testing "nested map stats"
    (is (= #::stats
           {:distinct-values #{},
            :sample-count 1,
            :pred-map {map? #::stats{:sample-count 1, :min-length 2, :max-length 2}},
            :map
            #::stats
            {:sample-count 1,
             :keyword-sample-count 1,
             :keys
             {:foo
              #::stats
              {:distinct-values #{1},
               :sample-count 1,
               :pred-map {integer? #::stats{:sample-count 1,:min 1,:max 1}}},
              :bar
              #::stats
              {:distinct-values #{},
               :sample-count 1,
               :pred-map
               {map?
                #::stats{:sample-count 1,:min-length 2,:max-length 2}},
               :map
               #::stats
               {:sample-count 1,
                :keyword-sample-count 1,
                :keys
                {:baz
                 #::stats
                 {:distinct-values
                  #{2},
                  :sample-count 1,
                  :pred-map {integer? #::stats{:sample-count 1,:min 2,:max 2}}},
                 :boo
                 #::stats
                 {:distinct-values #{3},
                  :sample-count 1,
                  :pred-map {integer? #::stats{:sample-count 1,:min 3,:max 3}}}}}}}}}
           (stats/collect [{:foo 1 :bar {:baz 2 :boo 3}}]))))

  (is (stats/collect (gen/sample (s/gen integer?) 1000)))
  (is (stats/collect (gen/sample (s/gen (s/coll-of integer?)) 1000)))
  (is (stats/collect (gen/sample (s/gen ::person/person) 100))))
