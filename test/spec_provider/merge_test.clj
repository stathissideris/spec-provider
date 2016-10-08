(ns spec-provider.merge-test
  (:require [spec-provider.merge :refer :all]
            [clojure.test :refer :all]
            [clojure.spec.test :as stest]
            [spec-provider.stats :as st]))

(stest/instrument [`merge-with-fns
                   `merge-pred-stats
                   `merge-pred-map
                   `merge-stats])
(stest/check [`merge-with-fns
              `merge-pred-stats
              `merge-pred-map
              `merge-stats])

(deftest test-merge-pred-stats
  (is (= #:spec-provider.stats
         {:sample-count 25
          :min          2
          :max          100
          :min-length   0
          :max-length   20}
         (merge-pred-stats

          #:spec-provider.stats
          {:sample-count 15
           :min          2
           :max          80
           :min-length   0
           :max-length   18}
          #:spec-provider.stats
          {:sample-count 10
           :min          10
           :max          100
           :min-length   2
           :max-length   20}))))

(deftest test-merge-stats
  (is (= #::st
         {:name                      "foo"
          :sample-count              45
          :distinct-values           #{:c :b :a}
          :keys                      {:a {::st/name "bar"}
                                      :b {::st/name "baz"}}
          :pred-map
          {:a #::st{:sample-count 35
                    :min          1
                    :max          100
                    :min-length   0
                    :max-length   20}}
          :hit-distinct-values-limit true
          :hit-key-size-limit        false
          :elements                  [:a :b :c]}
         (merge-stats
          #::st
          {:name                      "foo"
           :sample-count              10
           :distinct-values           #{:a :b}
           :keys                      {:a {::st/name "bar"}}
           :pred-map
           {:a #::st{:sample-count 15
                     :min          2
                     :max          80
                     :min-length   0
                     :max-length   18}}
           :hit-distinct-values-limit true
           :hit-key-size-limit        false
           :elements                  [:a :b]}
          #::st
          {:name                      "foo"
           :sample-count              35
           :distinct-values           #{:c}
           :keys                      {:b {::st/name "baz"}}
           :pred-map
           {:a #::st{:sample-count 20
                     :min          1
                     :max          100
                     :min-length   10
                     :max-length   20}}
           :hit-distinct-values-limit false
           :hit-key-size-limit        false
           :elements                  [:c]}))))
