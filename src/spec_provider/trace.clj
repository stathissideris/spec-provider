(ns spec-provider.trace
  (:require [spec-provider.stats :as stats]))

(defn record-arg-values! [fn-name a args]
  (swap! a update-in [fn-name :args] #(stats/update-stats % args)))

(defn record-return-value! [fn-name a val]
  (swap! a update-in [fn-name :return] #(stats/update-stats % val))
  val)

(defn- arg-name [arg]
  (cond (symbol? arg) arg
        (map? arg) (:as arg)
        (vector? arg) (last arg)))

(defn record-arg-names! [fn-name a arg-names]
  (swap! a assoc-in [fn-name :arg-names] arg-names))

(defn- fix-arg [idx arg]
  (cond (map? arg)
        (if (:as arg)
          arg
          (assoc arg :as (symbol (str "map" idx))))
        (vector? arg)
        (if (= :as (first (take-last 2 arg)))
          arg
          (into arg [:as (symbol (str "vec" idx))]))
        :else
        arg))

(defn- fix-args [args]
  (mapv #(fix-arg %1 %2) (range) args))

(defn- instrument-body [fn-name a body]
  (let [args (fix-args (first body))]
    `(~args
      (record-arg-names! ~fn-name ~a (quote ~(mapv arg-name args)))
      (record-arg-values! ~fn-name ~a ~(mapv arg-name args))
      ~@(drop 1 (butlast body))
      (record-return-value! ~fn-name ~a ~(last body)))))

(defmacro instrument [a defn-code]
  (assert (= 'defn (first defn-code)))
  (let [fn-name  (second defn-code)
        start    (if (string? (nth defn-code 2))
                   (take 3 defn-code)
                   (take 2 defn-code))
        doc      (if (string? (nth defn-code 2)) (nth defn-code 2) "")
        rest     (if (string? (nth defn-code 2))
                   (drop 3 defn-code)
                   (drop 2 defn-code))
        bodies   (if (vector? (first rest))
                   [rest]
                   (vec rest))
        atom-sym (gensym)]
    `(let [~atom-sym a]
       (defn ~fn-name ~doc
         ~@(map #(instrument-body (str *ns* "/" fn-name) atom-sym %) bodies)))))

(def a (atom {}))

(instrument a
  (defn foo "doc" [a b [v1 v2] c d {:keys [foo bar]}]
    (swap! (atom []) conj 1)
    (swap! (atom []) conj 2)
    (* d (+ a b c))))
