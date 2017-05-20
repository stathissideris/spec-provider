(ns spec-provider.provider
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha]
            [spec-provider.stats :as stats]
            [spec-provider.merge :refer [merge-stats]]
            [clojure.walk :as walk]
            [clojure.pprint :refer [pprint]]))

;;this means that if the count of the distinct values is less than 10%
;;of the count of total values, then the attribute is considered an
;;enumeration
(def enum-threshold 0.1)

(def pred->form
  {string?      'clojure.core/string?
   double?      'clojure.core/double?
   stats/float? 'clojure.core/float?
   integer?     'clojure.core/integer?
   keyword?     'clojure.core/keyword?
   boolean?     'clojure.core/boolean?
   set?         'clojure.core/set?
   map?         'clojure.core/map?})

(def pred->name
  {string?      :string
   double?      :double
   stats/float? :float
   integer?     :integer
   keyword?     :keyword
   boolean?     :boolean
   set?         :set
   map?         :map})

(defn- wrap-nilable [nilable? form]
  (if-not nilable?
    form
    (list `s/nilable form)))

(defn summarize-leaf [{:keys [::stats/pred-map
                              ::stats/sample-count
                              ::stats/distinct-values
                              ::stats/hit-distinct-values-limit] :as stats}]
  (let [nilable? (get pred-map nil?)
        pred-map (dissoc pred-map nil?)]
    (cond (stats/empty-sequential? stats)
          `empty?

          (and
           (not hit-distinct-values-limit)
           (>= enum-threshold
               (/ (float (count distinct-values))
                  (float sample-count))))
          distinct-values

          (= 1 (count pred-map))
          (wrap-nilable nilable? (pred->form (ffirst pred-map)))

          (> (count pred-map) 1)
          (concat
           (list 'clojure.spec.alpha/or)
           (->> (map first pred-map)
                (map (juxt pred->name
                           (comp (partial wrap-nilable nilable?)
                                 pred->form)))
                (remove (comp nil? first))
                (sort-by first)
                (apply concat))))))

(defn- qualified-key? [k] (some? (namespace k)))
(defn- qualify-key [k ns] (keyword (str ns) (name k)))

(defn summarize-keys [keys-stats ns]
  (let [highest-freq (apply max (map ::stats/sample-count (vals keys-stats)))
        extract-keys (fn [filter-fn]
                       (->> keys-stats
                            (filter filter-fn)
                            (mapv #(qualify-key (key %) ns))
                            sort
                            vec
                            not-empty))
        req          (extract-keys
                      (fn [[k v]] (and (qualified-key? k) (= (::stats/sample-count v) highest-freq))))
        opt          (extract-keys
                      (fn [[k v]] (and (qualified-key? k) (< (::stats/sample-count v) highest-freq))))
        req-un       (extract-keys
                      (fn [[k v]] (and (not (qualified-key? k)) (= (::stats/sample-count v) highest-freq))))
        opt-un       (extract-keys
                      (fn [[k v]] (and (not (qualified-key? k)) (< (::stats/sample-count v) highest-freq))))]
    (cond-> (list 'clojure.spec.alpha/keys)
      req (concat [:req req])
      opt (concat [:opt opt])
      req-un (concat [:req-un req-un])
      opt-un (concat [:opt-un opt-un]))))

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
                         keys-stats          ::stats/keys
                         elements-coll-stats ::stats/elements-coll
                         elements-pos-stats  ::stats/elements-pos
                         :as                 stats}
                        spec-ns]
  (let [nilable?   (get-in stats [::stats/pred-map nil?])
        leaf-stats (cond-> stats
                     keys-stats          (update ::stats/pred-map dissoc map?)
                     elements-coll-stats (update ::stats/pred-map dissoc sequential?)
                     elements-pos-stats  (update ::stats/pred-map dissoc sequential?))
        summaries
        (remove
         (comp nil? second)
         [(when keys-stats [:map (wrap-nilable nilable? (summarize-keys keys-stats spec-ns))])
          (when elements-coll-stats [:collection (wrap-nilable nilable? (summarize-coll-elements elements-coll-stats spec-ns))])
          (when elements-pos-stats [:cat (summarize-pos-elements elements-pos-stats spec-ns)])
          [:simple (let [s (summarize-leaf leaf-stats)]
                     (if-not (coll? s) s (not-empty s)))]])]
    (if (= 1 (count summaries))
      (-> summaries first second)
      (concat (list `s/or) (apply concat (sort-by first summaries))))))

(defn- maybe-promote-spec
  "Promote s/cat to top level if it's wrapped inside a (s/spec ...)"
  [spec]
  (if (and (seq? spec) (= `s/spec (first spec)))
    (second spec)
    spec))

(defn- core? [x]
  (when (and x (symbol? x)) (= "clojure.core" (namespace x))))

(defn- optimize-named
  "Replace instances of nested specs that are identical to named
  specs with the name"
  [spec named-specs]
  (walk/postwalk
   (fn [x]
     (if (core? x)
       x ;;don't replace core preds, we are looking for more complex specs
       (if-let [spec-name (get named-specs x)]
         spec-name
         x))) spec))

(defn- flatten-stats [stats spec-name]
  (let [children (comp (some-fn ::stats/keys
                                ::stats/elements-pos
                                (comp ::stats/keys ::stats/elements-coll)) second)]
   (reduce
    (fn [flat [stat-name stats :as node]]
      (if (and (not (number? stat-name)) ;;stat "name" is number for ::stats/elements-pos
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
                     (maybe-promote-spec (summarize-stats* stats spec-ns))])))
        spec->name
        (zipmap (map second specs) (map first specs))

        optimized-specs
        (map (fn [n s] [n (optimize-named s (dissoc spec->name s))])
             (map first specs) (map second specs))]

    (map (fn [[spec-name spec]]
           (list `s/def spec-name spec)) optimized-specs)))

(defn infer-specs
  ([data spec-name]
   (infer-specs data spec-name {}))
  ([data spec-name options]
   (when-not (namespace spec-name)
     (throw
      (ex-info (format "invalid spec-name %s - should be fully-qualified keyword" (str spec-name))
               {:spec-name spec-name})))
   (summarize-stats (stats/collect-stats data (::stats/options options)) spec-name)))

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
