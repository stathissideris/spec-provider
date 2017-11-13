(ns spec-provider.merge
  (:require [spec-provider.stats :as st]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]))

(comment
  (require '[clojure.spec.test.alpha :as stest])
  (stest/instrument `merge-with-fns))

(declare merge-stats)
(declare merge-stats-fns)

(defn- merge-keys-stats [a b]
  (merge-with merge-stats a b))

(defn merge-with-fns
  "Like merge-with but the merge function for colliding attributes is
  looked up from a map based on the key."
  [fns a b]
  (cond
    (and (nil? a) (nil? b)) nil
    (nil? a) b
    (nil? b) a
    :else
    (reduce
     (fn [a [k v]]
       (if (contains? a k)
         (let [fun (fns k)]
           (when-not fun
             (throw (ex-info (str "Don't know how to merge " k)
                             {:key k
                              :a   a
                              :b   b})))
           (assoc a k (fun (get a k) v)))
         (assoc a k v))) a b)))
(s/fdef merge-with-fns
        :args (s/cat
               :fns (s/map-of keyword? fn?)
               :a (s/nilable (s/map-of keyword? any?))
               :b (s/nilable (s/map-of keyword? any?)))
        :ret (s/map-of keyword? any?))

(defn merge-pred-stats [a b]
  (merge-with-fns merge-stats-fns a b))
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

(defn- merge-elements-coll-stats [a b]
  (merge-with-fns merge-stats-fns a b))
(s/fdef merge-elements-coll-stats
        :args (s/cat :a ::st/elements-coll
                     :b ::st/elements-coll)
        :ret ::st/elements-coll)

(def merge-stats-fns
  #:spec-provider.stats
  {:name                      (fn [a _] a)
   :sample-count              +
   :min                       min
   :max                       max
   :min-length                min
   :max-length                max
   :distinct-values           into
   :map                       #(merge-with-fns merge-stats-fns %1 %2)
   :keys                      merge-keys-stats
   :empty-sample-count        +
   :keyword-sample-count      +
   :non-keyword-sample-count  +
   :mixed-sample-count        +
   :elements-coll             merge-elements-coll-stats
   :elements-set              merge-elements-coll-stats
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
        :args (s/cat :a (s/nilable ::st/stats)
                     :b (s/nilable ::st/stats))
        :ret ::st/stats)
