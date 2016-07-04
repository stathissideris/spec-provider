(ns spec-provider.merge
  (:require [spec-provider.stats :as st]
            [clojure.set :as set]))

(declare merge-stats)

(defn- merge-keys [a b]
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

(def merge-pred-fns
  #:spec-provider.stats
  {:sample-count +
   :min          min
   :max          max
   :min-length   min
   :max-length   max})

(defn- merge-pred-stats [a b]
  (merge-with-fns merge-pred-fns a b))

(defn- merge-pred-map [a b]
  (merge-with merge-pred-stats a b))

(def merge-stats-fns
  #:spec-provider.stats
  {:name                      (fn [a _] a)
   :sample-count              +
   :distinct-values           into
   :keys                      merge-keys
   :pred-map                  merge-pred-map
   :hit-distinct-values-limit #(or %1 %2)
   :hit-key-size-limit        #(or %1 %2)
   :elements                  concat})

(defn merge-stats [a b]
  (merge-with-fns merge-stats-fns a b))
