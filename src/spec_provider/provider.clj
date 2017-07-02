(ns spec-provider.provider
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha]
            [spec-provider.stats :as stats]
            [spec-provider.merge :refer [merge-stats]]
            [spec-provider.rewrite :as rewrite]
            [clojure.walk :as walk]
            [clojure.pprint :refer [pprint]]))

;;this means that if the count of the distinct values is less than 10%
;;of the count of total values, then the attribute is considered an
;;enumeration
(def ^:dynamic enum-threshold 0.1)

(def pred->form
  {string?                  `string?
   double?                  `double?
   stats/float?             `float?
   integer?                 `integer?
   keyword?                 `keyword?
   boolean?                 `boolean?
   set?                     `set?
   map?                     `map?
   symbol?                  `symbol?
   stats/none-of-the-above? `any?})

(def pred->name
  {string?                  :string
   double?                  :double
   stats/float?             :float
   integer?                 :integer
   keyword?                 :keyword
   boolean?                 :boolean
   set?                     :set
   map?                     :map
   symbol?                  :symbol
   stats/none-of-the-above? :any})

(defn- wrap-nilable [nilable? form]
  (if-not nilable?
    form
    (list `s/nilable form)))

(defn summarize-leaf [{:keys [::stats/pred-map
                              ::stats/sample-count
                              ::stats/distinct-values
                              ::stats/hit-distinct-values-limit] :as stats}]
  (let [nilable? (get pred-map nil?)
        pred-map (dissoc pred-map nil? set?)]
    (cond (stats/empty-sequential? stats)
          `seq? ;;don't enforce empty

          (and
           (not hit-distinct-values-limit)
           (>= enum-threshold
               (/ (float (count distinct-values))
                  (float sample-count))))
          (wrap-nilable nilable? (disj distinct-values nil))

          (= 1 (count pred-map))
          (wrap-nilable nilable? (pred->form (ffirst pred-map)))

          (> (count pred-map) 1)
          (concat
           (list 'clojure.spec.alpha/or)
           (->> (map first pred-map)
                (map (juxt pred->name
                           (comp (partial wrap-nilable nilable?)
                                 pred->form)))
                ;;(remove (comp nil? first))
                (sort-by first)
                (apply concat))))))

(defn- qualified-key? [k] (some? (namespace k)))
(defn- qualify-key [k ns] (keyword (str ns) (name k)))

(declare summarize-stats*)
(defn- summarize-non-keyword-map [keys-stats ns]
  (list `s/map-of
        (summarize-stats* (stats/collect (keys keys-stats)) ns)
        (summarize-stats* (reduce merge-stats (vals keys-stats)) ns)))

(defn- summarize-keys [keys-stats sample-count ns]
  (let [extract-keys (fn [filter-fn]
                       (->> keys-stats
                            (filter filter-fn)
                            (mapv #(qualify-key (key %) ns))
                            sort
                            vec
                            not-empty))
        req          (extract-keys
                      (fn [[k v]] (and (qualified-key? k) (= (::stats/sample-count v) sample-count))))
        opt          (extract-keys
                      (fn [[k v]] (and (qualified-key? k) (< (::stats/sample-count v) sample-count))))
        req-un       (extract-keys
                      (fn [[k v]] (and (not (qualified-key? k)) (= (::stats/sample-count v) sample-count))))
        opt-un       (extract-keys
                      (fn [[k v]] (and (not (qualified-key? k)) (< (::stats/sample-count v) sample-count))))]
    (cond-> (list `s/keys)
      req (concat [:req req])
      opt (concat [:opt opt])
      req-un (concat [:req-un req-un])
      opt-un (concat [:opt-un opt-un]))))

(defn- keyword-map-stats [key-stats]
  (reduce-kv (fn [m k v] (if (keyword? k) (assoc m k v) m)) {} key-stats))

(defn- non-keyword-map-stats [key-stats]
  (reduce-kv (fn [m k v] (if-not (keyword? k) (assoc m k v) m)) {} key-stats))

(defn- summarize-map-stats [{:keys [::stats/sample-count
                                    ::stats/keys
                                    ::stats/empty-sample-count
                                    ::stats/keyword-sample-count
                                    ::stats/non-keyword-sample-count
                                    ::stats/mixed-sample-count] :as map-stats} ns]
  (let [summaries
        (cond-> []
          (and empty-sample-count (not keyword-sample-count))
          (conj [:empty `(s/and empty? map?)])

          non-keyword-sample-count
          (conj [:non-keyword-map (summarize-non-keyword-map (non-keyword-map-stats keys) ns)])

          keyword-sample-count
          (conj [:keyword-map (summarize-keys (keyword-map-stats keys)
                                              (+ (or empty-sample-count 0) keyword-sample-count)
                                              ns)]))]
    (if (= 1 (count summaries))
      (-> summaries first second)
      (concat (list `s/or) (apply concat summaries)))))

(defn- add-kind [kind spec]
  (if (and (list? spec) kind)
    (concat spec (list :kind kind))
    spec))

(declare summarize-stats*)
(defn- summarize-coll-elements [stats spec-ns]
  (list `s/coll-of (summarize-stats* stats spec-ns)))

(defn- summarize-pos-elements [stats spec-ns]
  (list
   `s/spec ;;TODO would nice for this to not happen at top level
   (concat
    (list `s/cat)
    (interleave
     (map #(keyword (str "el" %)) (range))
     (map #(summarize-stats* % spec-ns) (vals (sort-by key stats))))))) ;;TODO extra rules for optional elements

(defn summarize-stats* [{pred-map            ::stats/pred-map
                         map-stats           ::stats/map
                         sample-count        ::stats/sample-count
                         elements-coll-stats ::stats/elements-coll
                         elements-pos-stats  ::stats/elements-pos
                         elements-set-stats  ::stats/elements-set
                         :as                 stats}
                        spec-ns]
  (let [nilable?   (get-in stats [::stats/pred-map nil?])
        leaf-stats (cond-> stats
                     map-stats           (update ::stats/pred-map dissoc map?)
                     elements-coll-stats (update ::stats/pred-map dissoc sequential?)
                     elements-pos-stats  (update ::stats/pred-map dissoc sequential?))
        summaries
        (remove
         (comp nil? second)
         [(when map-stats [:map (wrap-nilable nilable? (summarize-map-stats map-stats spec-ns))])
          (when elements-coll-stats
            [:collection
             (->> (summarize-coll-elements elements-coll-stats spec-ns)
                  (wrap-nilable nilable?))])
          (when elements-pos-stats [:cat (summarize-pos-elements elements-pos-stats spec-ns)])
          (when elements-set-stats
            [:set
             (->> (summarize-coll-elements elements-set-stats spec-ns)
                  (add-kind `set?)
                  (wrap-nilable nilable?))])
          [:simple (let [s (summarize-leaf leaf-stats)]
                     (if-not (coll? s) s (not-empty s)))]])]
    (cond (and (zero? (count summaries))
               (or elements-coll-stats elements-pos-stats elements-set-stats))
          `empty?
          (zero? (count summaries))
          `any?
          (= 1 (count summaries))
          (-> summaries first second)
          :else
          (concat (list `s/or) (apply concat (sort-by first summaries))))))

(defn- maybe-promote-spec ;;TODO move to rewrite namespace
  "Promote s/cat to top level if it's wrapped inside a (s/spec ...)"
  [spec]
  (if (and (seq? spec) (= `s/spec (first spec)))
    (second spec)
    spec))

(defn- flatten-stats [stats spec-name]
  (let [children (comp (some-fn (comp ::stats/keys ::stats/map)
                                ::stats/elements-pos
                                (comp ::stats/keys ::stats/map ::stats/elements-coll)) second)]
   (reduce
    (fn [flat [stat-name stats :as node]]
      (if (and (keyword? stat-name) ;;stat "name" is number for ::stats/elements-pos
               (::stats/pred-map stats))
        (-> flat
            (update :order #(cons stat-name %))
            ;;TODO warn on "incompatible" merge
            (update-in [:stats stat-name] #(merge-stats % stats)))
        flat))
    {:order ()
     :stats {}}
    (tree-seq children children [spec-name stats]))))

(defn summarize-stats [stats spec-name]
  (let [spec-ns               (namespace spec-name)
        {:keys [order stats]} (flatten-stats stats spec-name)
        specs
        (->> (map #(vector % (get stats %)) (distinct order))
             (map (fn [[stat-name stats]]
                    [(keyword spec-ns (name stat-name))
                     (maybe-promote-spec (summarize-stats* stats spec-ns))]))
             (map (fn [[spec-name spec]]
                    (list `s/def spec-name spec))))]
    (-> specs
        rewrite/all-nilable-or
        rewrite/known-names)))
(s/fdef summarize-stats
        :args (s/cat :stats (s/nilable ::stats/stats)
                     :spec-name qualified-keyword?))

(defn infer-specs
  ([data spec-name]
   (infer-specs data spec-name {}))
  ([data spec-name options]
   (when-not (namespace spec-name)
     (throw
      (ex-info (format "invalid spec-name %s - should be fully-qualified keyword" (str spec-name))
               {:spec-name spec-name})))
   (let [stats (stats/collect data (::stats/options options))]
     (s/valid? ::stats/stats stats)
     (summarize-stats stats spec-name))))

(defn unqualify-spec [spec domain-ns clojure-spec-ns]
  (let [domain-ns (str domain-ns)
        clojure-spec-ns (str clojure-spec-ns)]
    (walk/postwalk
     (fn [x]
       (cond (and (symbol? x) (= "clojure.spec.alpha" (namespace x)))
               (symbol clojure-spec-ns (name x))
             (and (symbol? x) (= "clojure.core" (namespace x)))
               (symbol (name x))
             (and (keyword? x) (= domain-ns (namespace x)))
               (symbol (str "::" (name x))) ;;nasty hack to get the printer to print ::foo
             :else x))
     spec)))

(defn pprint-specs [specs domain-ns clojure-spec-ns]
  (doseq [spec (map #(unqualify-spec % domain-ns clojure-spec-ns) specs)]
    (pprint spec)))

;;infer-specs for nested maps algo:
;; 0. assign names to all nested maps based on the key
;;    they're under
;; 1. collect all nested maps stats. Also, root map (if map)
;; 2. collect all attribute stats from all maps
;; 3. (maybe) merge the attribute stats of the ones that have the same name
;; 4. infer spec for each attribute
;; 5. infer spec for each map keyset

;;(s/form (s/or :numeric (s/and integer? pos?) :string string?))
