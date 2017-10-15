(ns spec-provider.rewrite
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [spec-provider.stats :as stats]
            [spec-provider.util :as util]))

(defn wrap [form wrapper]
  (list wrapper form))

(defn- nilable? [x]
  (and (list? x) (= `s/nilable (first x))))

(defn- or-preds [x]
  (->> x (take-nth 2) rest))

(defn- or-names [x]
  (->> x rest (take-nth 2)))

(defn- zip-or [names preds]
  (concat (list `s/or) (mapcat vector names preds)))

(def cat-names or-names)
(def cat-preds or-preds)
(defn zip-cat [names preds]
  (concat (list `s/cat) (mapcat vector names preds)))

(defn- or? [x]
  (and (seq? x) (= `s/or (first x))))

(defn all-nilable-or* [x]
  (if-not (every? nilable? (or-preds x))
    x
    (list `s/nilable (zip-or (or-names x) (map second (or-preds x))))))

(defn all-nilable-or [form]
  (walk/postwalk
   (fn [form]
     (if (or? form)
       (all-nilable-or* form)
       form))
   form))

(defn maybe-promote-spec
  "Promote spec to top level if it's wrapped inside a (s/spec ...)"
  [spec]
  (if (and (seq? spec) (= `s/spec (first spec)))
    (second spec)
    spec))

(defn- spec-name [x]
  (second x))

(defn- spec-body [x]
  (nth x 2))

(defn- core? [x]
  (when (and x (symbol? x)) (= "clojure.core" (namespace x))))

(defn known-names
  "Replace instances of nested specs that are identical to named
  specs with the name"
  [specs]
  (let [form->name (zipmap (map spec-body specs)
                           (map spec-name specs))]
    (map
     (fn [spec]
       (let [form->name (dissoc form->name (spec-body spec))]
         (walk/postwalk
          (fn [x]
            (if (core? x)
              x ;;don't replace core preds, we are looking for more complex specs
              (if-let [spec-name (get form->name x)]
                spec-name
                x)))
          spec)))
     specs)))

(defn- set-body [spec new-body]
  (apply list (-> spec vec (assoc 2 new-body))))

(defn merge-same-name-defs
  "Merge top-level defs with the same name under one spec with an
  s/or. Preserves order of specs."
  [specs]
  (let [order (distinct (map spec-name specs))
        cases (map #(keyword (str "case-" %)) (range))]
    (map
     (->> specs
          (group-by spec-name)
          (reduce-kv (fn [m k v]
                       (if (= 1 (count v))
                         (assoc m k (first v))
                         (assoc m k (set-body (first v)
                                              `(s/or ~@(interleave cases (map spec-body v))))))) {}))
     order)))

(def pred->name
  {'clojure.core/string?       :string
   'clojure.core/double?       :double
   'spec-provider.stats/float? :float
   'clojure.core/integer?      :integer
   'clojure.core/bigdec?       :bigdec
   'clojure.core/keyword?      :keyword
   'clojure.core/boolean?      :boolean
   'clojure.core/set?          :set
   'clojure.core/map?          :map
   'clojure.core/symbol?       :symbol})

(defn- set-or-names [or-form new-names]
  `(s/or ~@(interleave new-names
                       (or-preds or-form))))

(defn- fix-or-names*
  "Works on a single or expression"
  [or-form]
  (set-or-names or-form
                (map #(or (pred->name %1) %2)
                     (or-preds or-form)
                     (or-names or-form))))

(defn fix-or-names
  "Works on the whole tree of specs"
  [specs]
  (walk/postwalk
   (fn [form]
     (if (or? form)
       (fix-or-names* form)
       form))
   specs))

(defn flatten-ors*
  "Works on a single or expression"
  [or-form]
  `(s/or ~@(mapcat (fn [[name pred]]
                     (if (or? pred)
                       (rest pred)
                       [name pred]))
                   (partition 2 (rest or-form)))))

(defn flatten-ors
  "Works on the whole tree of specs"
  [specs]
  (walk/postwalk
   (fn [form]
     (if (or? form)
       (flatten-ors* form)
       form))
   specs))

(defn distinct-or*
  [or-form]
  `(s/or ~@(->> (rest or-form)
                (partition 2)
                (util/distinct-by second)
                (apply concat))))

(defn distinct-ors
  [specs]
  (walk/postwalk
   (fn [form]
     (if (or? form)
       (distinct-or* form)
       form))
   specs))
