(ns spec-provider.util
  (:require [clojure.zip :as zip]
            [clojure.spec.alpha :as s]
            [clojure.pprint :as pp]))

(defn generic-zipper
  "Walks vectors, lists, maps, and maps' keys and values
  individually. Take care not to replace a keypair with a single
  value (will throw an exception)."
  [x]
  (zip/zipper
   (some-fn sequential? map?)
   seq
   (fn [node children]
     (cond (vector? node) (vec children)
           (seq? node) (seq children)
           (map? node) (into {} children)))
   x))

(defn walk-reduce [f val zipper]
  (loop [zipper zipper
         val    val]
    (if (zip/end? zipper)
      val
      (recur (zip/next zipper) (f val (zip/node zipper))))))

(comment
  (walk-reduce
   (fn [acc node]
     (if (and (vector? node) (= :a (first node)))
       (conj acc node)
       acc))
   []
   (generic-zipper
    [[:a 9]
     [:b [:a 10]]
     [:a 11]
     [:b [:c [:a 22]]]])))

(comment
  (reduce
   (fn [acc node]
     (if (and (vector? node) (= :a (first node)))
       (cons node acc)
       acc))
   ()
   (tree-seq
    (some-fn sequential? map?)
    seq
    [[:a 9]
     [:b [:a 10]]
     [:a 11]
     [:b
      [:a 1]
      [:c [:a 22]]]])))


(defn best-match [x]
  (let [all
        (->> (s/registry)
             keys
             (filter keyword?)
             (map #(vector % (count (or (::s/problems (s/explain-data % x)) []))))
             (sort-by second))]
    (pp/pprint all)))

(defn distinct-by [fun coll]
  (let [step (fn step [xs seen]
               (lazy-seq
                ((fn [[f :as xs] seen]
                   (when-let [s (seq xs)]
                     (let [key (fun f)]
                      (if (contains? seen key)
                        (recur (rest s) seen)
                        (cons f (step (rest s) (conj seen key)))))))
                 xs seen)))]
    (step coll #{})))
