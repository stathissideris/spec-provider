(ns spec-provider.trace
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [spec-provider.stats :as stats]
            [spec-provider.provider :as provider]
            [spec-provider.merge :as merge]
            [spec-provider.rewrite :as rewrite]
            [pretty-spec.core :as pp]))

(defonce reg (atom {}))

(defn record-arg-values! [fn-name a args]
  (let [arities (-> @a (get fn-name) :arg-names keys set (disj :var-args))]
    (if (get arities (count args))
      (swap! a update-in [fn-name :args (count args)] #(stats/update-stats % args {::stats/positional true}))
      (swap! a update-in [fn-name :args :var-args] #(stats/update-stats % args {::stats/positional true})))))

(defn record-return-value! [fn-name a val]
  (swap! a update-in [fn-name :return] #(stats/update-stats % val {}))
  val)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::as (s/cat :as #{:as}
                   :name symbol?))

(s/def ::simple-arg symbol?)
(s/def ::vector-destr (s/and vector?
                             (s/cat :args    (s/* ::arg)
                                    :varargs (s/? ::var-args)
                                    :as      (s/? ::as))))

(s/def ::map-destr (s/or :keys-destr ::keys-destr
                         :syms-destr ::syms-destr
                         :strs-destr ::strs-destr
                         :explicit-destr ::explicit-destr))

(s/def ::map-as symbol?)

(s/def ::or (s/map-of simple-symbol? any?))

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
                    (s/or :simple ::simple-arg
                          :map-destr ::map-destr
                          :vector-destr ::vector-destr)))

(s/def ::var-args (s/cat :amp #{'&} :arg ::arg))

(s/def ::args (s/cat :args (s/* ::arg) :var-args (s/? ::var-args)))

(comment ;; to test
  (pprint (s/conform
           ::args
           '[a b [[deep1 deep2] v1 v2 & rest :as foo]
             c d {:keys [foo bar] :as foo2 :or {foo 1 bar 2}}
             {a "foo" [x y] :point c :cc}
             [& rest]])))

(comment
  (->> (s/registry) keys (sort-by str) pprint)

  (pprint (s/form (s/get-spec :clojure.core/let)))

  (pprint (s/form (s/get-spec :clojure.core.specs/arg-list)))
  (pprint (s/form (s/get-spec :clojure.core.specs/binding-form)))
  (pprint (s/form (s/get-spec :clojure.core.specs/map-binding-form)))
  (pprint (s/form (s/get-spec :clojure.core.specs/map-bindings)))
  (pprint (s/form (s/get-spec :clojure.core.specs/map-special-binding)))
  (pprint (s/form (s/get-spec :clojure.core.specs/or)))
  (pprint (s/form (s/get-spec :clojure.core.specs/map-binding))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;   apply names  ;;;;;;;;;;;;;

(defn- arg-names [args]
  (cond
    (nil? args)
    nil

    (and (vector? args) (-> args first (= :simple)))
    (second args)

    (and (vector? args) (-> args first (= :map-destr)))
    (-> args second second :as)

    (and (vector? args) (-> args first (= :vector-destr)))
    (-> args second :as :name)

    (:args args)
    (conj (mapv arg-names (:args args))
          (-> args :var-args :arg arg-names))))

;;;

(defn- spec-form-type [x]
  (cond (seq? x) (first x)
        (and (keyword? x) (namespace x)) :spec-ref
        :else :default))

(defmulti set-names (fn [spec-form args]
                      (spec-form-type spec-form)))

(defmethod set-names :default [spec-form _] spec-form)

(defmethod set-names `s/spec [spec-form args]
  `(s/spec ~(set-names (second spec-form) (-> args second)))) ;;TODO: assumption: (second spec-form) is an s/cat

(defn- set-cat-pair-name [[old-name pred] arg]
  (let [pred-name (arg-names pred)]
    [(or (keyword (arg-names arg))
         pred-name
         old-name)
     (set-names pred arg)]))

(defmethod set-names `s/cat
  [spec-form args]
  (let [args (not-empty
              (remove nil?
                      (concat (:args args)
                              [(-> args :var-args :arg)])))]
    (if-not args
      spec-form
      `(s/cat
        ~@(doall
           (mapcat (fn [pair arg]
                     (set-cat-pair-name pair arg))
                   (partition 2 (rest spec-form))
                   args))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- spec-provider-generated? [x]
  (-> x meta :spec-provider-generated true?))

(defn- extract-arg-names [args]
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (:as x) (spec-provider-generated? (:as x)))
       (dissoc x :as) ;;remove :as when generated by spec-provider
       x))
   (s/conform ::args args)))

(defn record-args! [fn-name a args]
  (if (get (set args) '&)
    (swap! a assoc-in [fn-name :arg-names :var-args] args)
    (swap! a assoc-in [fn-name :arg-names (count args)] args)))

(defn- add-as [x]
  (cond (not (map? x)) x
        (:as x) x
        :else (assoc x :as (with-meta (gensym "spec-provider-name-")
                             {:spec-provider-generated true}))))

(defn- handle-map-destruct [x]
  (if-not (map? x) x (:as x)))

(defn- instrument-body [fn-name atom-sym body]
  (let [args (mapv add-as (first body))]
    `(~args
      ~(let [[normal-args [_ var-arg]] (split-with #(not= '& %) args)]
         (if var-arg
           `(record-arg-values! ~fn-name ~atom-sym
                                (conj
                                 ~(mapv handle-map-destruct normal-args)
                                 ~(handle-map-destruct var-arg)))
           `(record-arg-values! ~fn-name ~atom-sym ~(mapv handle-map-destruct args))))
      ~@(drop 1 (butlast body))
      (record-return-value! ~fn-name ~atom-sym ~(last body)))))

(defn- instrument*
  ([defn-code]
   (instrument* 'spec-provider.trace/reg defn-code))
  ([trace-atom defn-code]
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
         atom-sym (gensym "spec-provider-atom-")
         fn-key   (str *ns* "/" fn-name)]
     `(let [~atom-sym ~trace-atom]
        (do
          ~@(for [body bodies]
              `(record-args! ~fn-key ~atom-sym (quote ~(first body)))))
        (defn ~fn-name ~doc
          ~@(map #(instrument-body fn-key atom-sym %) bodies))))))

(defmacro instrument
  ([& args]
   (apply instrument* args)))

(defn- spec-form [s]
  (nth s 2))

(defn- args-for-arity [fn-stats arity]
  (as-> fn-stats $
    (:arg-names $)
    (get $ arity)
    (s/conform ::args $)))

(defn- arity-key [arity]
  (if (= :var-args arity)
    :var-args
    (keyword (str "arity-" arity))))

(defn- arity-order [arity]
  (if (= :var-args arity)
    Integer/MAX_VALUE
    arity))

(defn- format-arity-spec [spec stats arity]
  (let [spec (-> spec last spec-form (set-names (args-for-arity stats arity)))]
    (if (= arity :var-args)
      (let [var-spec (rewrite/maybe-promote-spec (last spec))]
       (concat
        (butlast spec)
        [(rewrite/zip-cat (rewrite/cat-names var-spec)
                          (map #(rewrite/wrap % `s/?)
                               (rewrite/cat-preds var-spec)))]))
      spec)))

(defn- multi-arity-spec [arg-specs stats]
  `(s/or
    ~@(mapcat
       (fn [[arity spec]]
         [(arity-key arity) (format-arity-spec spec stats arity)])
       (sort-by (comp arity-order first) arg-specs))))

(defn fn-specs
  ([fn-name]
   (fn-specs reg fn-name))
  ([trace-atom fn-name]
   (let [stats        (get @trace-atom (str fn-name))
         arg-specs    (reduce-kv (fn [m k v]
                                   (assoc m k (provider/summarize-stats v (keyword fn-name))))
                                 {}
                                 (:args stats))
         return-specs (provider/summarize-stats (:return stats) (keyword fn-name))
         specs
         (concat
          (mapcat butlast (vals arg-specs))
          (butlast return-specs)
          [(list `s/fdef (symbol fn-name)
                 :args (cond (zero? (count arg-specs))
                             `(s/cat)
                             (= 1 (count arg-specs))
                             (-> arg-specs vals first (format-arity-spec stats (ffirst arg-specs)))
                             :else
                             (multi-arity-spec arg-specs stats))
                 :ret  (-> return-specs last spec-form))])]
     (-> specs
         rewrite/merge-same-name-defs
         rewrite/flatten-ors
         rewrite/distinct-ors
         rewrite/fix-or-names))))

(defn pprint-fn-specs
  ([fn-name domain-ns clojure-spec-ns]
   (pprint-fn-specs reg fn-name domain-ns clojure-spec-ns))
  ([trace-atom fn-name domain-ns clojure-spec-ns]
   (doseq [spec (fn-specs trace-atom fn-name)]
     (-> spec
         (provider/unqualify-spec domain-ns nil)
         (pp/pprint {:ns-aliases {"clojure.spec.alpha" (str clojure-spec-ns)}})))))

(defn clear-registry!
  ([]
   (clear-registry! reg))
  ([reg]
   (reset! reg {})))

(comment
  (instrument
   (defn foo "doc"
     ([a b c d e f g h i j & rest]
      (swap! (atom []) conj 1)
      (swap! (atom []) conj 2)
      ;;{:result (* d (+ a b c))}
      6)
     ([a b [[v1 v2] v3] c d {:keys [foo bar]} {:keys [baz] :as bazz}]
      (swap! (atom []) conj 1)
      (swap! (atom []) conj 2)
      ;;{:result (* d (+ a b c))}
      6)))
  )

(comment
  (pprint (s/conform ::args '[a b c d e f g h i j & rest]))
  (pprint (s/conform ::args '[a b [[v1 v2] v3] c d {:keys [foo bar]} {:keys [baz], :as bazz}]))
  (foo 10 20 30 40 50 60 70 80 90 100 110 "string")
  (foo 10 20 30 40 50 60 70 80 90 100 110 "string" :kkk)
  (foo 10 20 30 40 50 60 70 80 90 100 110 "string" {:bar :kkk})
  (foo 10 20 30 40 50 60 70 80 90 100)
  (foo 1 2 [[3 4] 5] 6 7 {:foo 8 :bar 9} {})
  (foo 1 2 [[3 4] 5] 6 7 {:foo 8 :bar 9} {:bar "also string"})
  (pprint-fn-specs 'spec-provider.trace/foo 'spec-provider.trace 's)
  (-> reg deref (get "spec-provider.trace/foo") :arg-names)
  )
