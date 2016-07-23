(ns spec-provider.merge
  (:require [spec-provider.stats :as st]
            [clojure.set :as set]
            [clojure.spec :as s]))

(comment
  (require '[clojure.spec.test :as stest])
  (stest/instrument `merge-with-fns))

(declare merge-stats)

(defn- merge-keys-stats [a b]
  (merge-with merge-stats a b))

(defn merge-with-fns
  "Like merge-with but the merge function for colliding attributes is
  looked up from a map based on the key."
  [fns a b]
  (reduce
   (fn [a [k v]]
     (if (contains? a k)
       (let [fun (fns k)]
         (assoc a k (fun (get a k) v)))
       (assoc a k v))) a b))
(s/fdef merge-with-fns
        :args (s/cat
               :fns (s/map-of keyword? fn?)
               :a (s/map-of keyword? any?)
               :b (s/map-of keyword? any?))
        :ret (s/map-of keyword? any?))

(def merge-pred-fns
  #:spec-provider.stats
  {:sample-count +
   :min          min
   :max          max
   :min-length   min
   :max-length   max})

(defn merge-pred-stats [a b]
  (merge-with-fns merge-pred-fns a b))
(s/fdef merge-pred-stats
        :args (s/cat :a ::st/pred-stats
                     :b ::st/pred-stats)
        :ret ::st/pred-stats)

(defn merge-pred-map [a b]
  (merge-with merge-pred-stats a b))
(s/fdef merge-pred-map
        :args (s/cat :a ::st/pred-map
                     :b ::st/pred-map)
        :ret ::st/pred-map)

(def merge-stats-fns
  #:spec-provider.stats
  {:name                      (fn [a _] a)
   :sample-count              +
   :distinct-values           into
   :keys                      merge-keys-stats
   :pred-map                  merge-pred-map
   :hit-distinct-values-limit #(or %1 %2)
   :hit-key-size-limit        #(or %1 %2)
   :elements                  concat})

(defn merge-stats [a b]
  (cond
    (nil? a) b
    (nil? b) a
    :else    (merge-with-fns merge-stats-fns a b)))
(s/fdef merge-stats
        :args (s/cat :a ::st/stats
                     :b ::st/stats)
        :ret ::st/stats)
