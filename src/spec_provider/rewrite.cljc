(ns spec-provider.rewrite
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]))

(defn- nilable? [x]
  (and (list? x) (= `s/nilable (first x))))

(defn- or-preds [x]
  (->> x (take-nth 2) rest))

(defn- or-names [x]
  (->> x rest (take-nth 2)))

(defn- zip-or [names preds]
  (concat (list `s/or) (mapcat vector names preds)))

(def ^:private cat-names or-names)
(def ^:private cat-preds or-preds)
(def ^:private zip-cat zip-or)

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
