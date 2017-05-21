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
