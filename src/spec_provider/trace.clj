(ns spec-provider.trace
  (:require [clojure.spec :as s]
            [clojure.walk :as walk]
            [spec-provider.stats :as stats]
            [spec-provider.provider :as provider]))

(defn- record-arg-values! [fn-name a args]
  (swap! a update-in [fn-name :args] #(stats/update-stats % args {:positional true})))

(defn- record-return-value! [fn-name a val]
  (swap! a update-in [fn-name :return] #(stats/update-stats % val {}))
  val)

(defn- arg-name [arg]
  (cond (symbol? arg) arg
        (map? arg) (:as arg)
        (vector? arg) (last arg)))

(defn- vector-has-as? [v]
  (= :as (first (take-last 2 v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::as (s/cat :as #{:as}
                   :name symbol?))

(s/def ::simple-arg symbol?)
(s/def ::vector-destr (s/and vector?
                             (s/cat :args    (s/* ::arg)
                                    :varargs (s/? (s/cat :amp #{'&}
                                                         :name symbol?))
                                    :as      (s/? ::as))))

(s/def ::map-destr (s/or :keys-destr ::keys-destr
                         :syms-destr ::syms-destr
                         :strs-destr ::strs-destr
                         :explicit-destr ::explicit-destr))

(s/def ::map-as symbol?)

(s/def ::or (s/map-of any? any?))

(s/def ::keys (s/coll-of symbol? :kind vector?))
(s/def ::keys-destr (s/keys :req-un [::keys] :opt-un [::map-as ::or]))

(s/def ::syms (s/coll-of symbol? :kind vector?))
(s/def ::syms-destr (s/keys :req-un [::syms] :opt-un [::map-as ::or]))

(s/def ::strs (s/coll-of symbol? :kind vector?))
(s/def ::strs-destr (s/keys :req-un [::strs] :opt-un [::map-as ::or]))

(s/def ::explicit-destr (s/and
                         (s/keys :opt-un [::map-as ::or] :conform-keys true)
                         (s/map-of (s/or :name symbol?
                                         :nested-destr ::arg)
                                   (s/or :destr-key any?) ;;or with single case just to get tagged value
                                   :conform-keys true)))

(s/def ::arg (s/and (complement #{'&})
                    (s/or :name ::simple-arg
                          :map-destr ::map-destr
                          :vector-destr ::vector-destr)))

(s/def ::args (s/coll-of ::arg :kind vector?))

(comment ;; to test
  (pprint (s/conform
           ::args
           '[a b [[deep1 deep2] v1 v2 & rest :as foo]
             c d {:keys [foo bar] :as foo2 :or {:foo 1 :bar 2}}
             {a "foo" [x y] :point c :cc}
             [& rest]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- extract-arg-names [args]
  (map
   (fn [arg]
     (walk/postwalk
      (fn [x]
        (cond
          (symbol? x) {:type :simple :name x}
          (vector? x) {:type :vector :name (last x) :elements (if (vector-has-as? x)
                                                                (drop-last 2 x) x)}
          (map? x)    {:type :map :name (:as x) :keys (keys x)}))
      arg))
   args))

(defn record-arg-names! [fn-name a arg-names]
  (swap! a assoc-in [fn-name :arg-names] arg-names))

(defn- fix-arg [idx arg] ;;TODO bug: assigned names may already exist
  (cond (map? arg)
        (if (:as arg)
          arg
          (assoc arg :as (symbol (str "map" idx))))
        (vector? arg)
        (if (vector-has-as? arg)
          arg
          (into arg [:as (symbol (str "vec" idx))]))
        :else
        arg))

(defn- fix-args [args]
  (mapv #(fix-arg %1 %2) (range) args))

(defn- instrument-body [fn-name a body]
  (let [args (fix-args (first body))]
    `(~args
      (record-arg-names! ~fn-name ~a (quote ~(extract-arg-names args)))
      (record-arg-values! ~fn-name ~a ~(mapv arg-name args))
      ~@(drop 1 (butlast body))
      (record-return-value! ~fn-name ~a ~(last body)))))

(defmacro instrument [trace-atom defn-code]
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
    `(let [~atom-sym ~trace-atom]
       (defn ~fn-name ~doc
         ~@(map #(instrument-body (str *ns* "/" fn-name) atom-sym %) bodies)))))

(defn- set-cat-names [cat-spec names]
  (let [parts (partition 2 (drop 1 (second cat-spec)))]
    (list
     `s/spec ;;TODO assumes that all s/cats are wrapped in an s/spec
     (concat (list `s/cat)
             (mapcat (fn [name [_ spec]] [name spec]) names parts)))))

(defn fn-spec [trace-atom fn-name]
  (let [fn-name      (str fn-name)
        stats        (get @trace-atom fn-name)
        arg-names    (map keyword (:arg-names stats))
        arg-stats    (:args stats)
        return-stats (:return stats)
        arg-specs    (set-cat-names
                      (-> (provider/summarize-stats arg-stats fn-name)
                          first (nth 2))
                      arg-names)
        return-spec (-> (provider/summarize-stats return-stats fn-name)
                        first (nth 2))]
    (list `s/fdef (symbol fn-name)
          :args arg-specs
          :ret return-spec)))

(defn pprint-fn-spec [trace-atom fn-name domain-ns clojure-spec-ns]
  (provider/pprint-specs [(fn-spec trace-atom fn-name)] domain-ns clojure-spec-ns))

(defonce a (atom {}))

(comment
 (instrument a
             (defn foo "doc" [a b [v1 v2] c d {:keys [foo bar]}]
               (swap! (atom []) conj 1)
               (swap! (atom []) conj 2)
               (* d (+ a b c)))))

(comment
  (foo 1 2 [3 4] 5 6 {:foo 7 :bar 8})
  (pprint-fn-spec a 'spec-provider.trace/foo)
  )
