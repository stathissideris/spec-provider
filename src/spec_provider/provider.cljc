(ns spec-provider.provider
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha]
            [spec-provider.stats :as stats]
            [spec-provider.merge :refer [merge-stats merge-pred-stats]]
            [spec-provider.rewrite :as rewrite]
            [clojure.walk :as walk]
            [clojure.pprint :refer [pprint]]))

;;this means that if the count of the distinct values is less than 10%
;;of the count of total values, then the attribute is considered an
;;enumeration
(def ^:dynamic enum-threshold 0.1)

(s/def ::range (s/or :switch boolean?
                     :names (s/coll-of qualified-keyword? :kind set?)))
(s/def ::options (s/keys :opt [::range]))

(def pred->form
  {string?                  `string?
   stats/double?            `double?
   stats/float?             `float?
   integer?                 `integer?
   #?@(:clj
       [decimal?            `decimal?])
   keyword?                 `keyword?
   boolean?                 `boolean?
   set?                     `set?
   map?                     `map?
   symbol?                  `symbol?
   inst?                    `inst?
   stats/none-of-the-above? `any?})

(def pred->name
  {string?                  :string
   stats/double?            :double
   stats/float?             :float
   integer?                 :integer
   #?@(:clj
       [decimal?            :decimal])
   keyword?                 :keyword
   boolean?                 :boolean
   set?                     :set
   map?                     :map
   symbol?                  :symbol
   inst?                    :inst
   stats/none-of-the-above? :any})

(def number-spec? #{`double?
                    `float?
                    `integer?
                    `decimal?})

(defn- wrap-nilable [nilable? form]
  (if-not nilable?
    form
    (list `s/nilable form)))

(defn- restrict-range [stats
                       spec-name
                       {:keys [::range] :as options}
                       spec]
  (if (and (number-spec? spec)
           (or (= true range)
               (and (set? range) (get range spec-name))))
    (let [stats  (-> stats ::stats/pred-map vals)
          merged (if (= 1 (count stats)) (first stats) (reduce merge-pred-stats stats))
          min    (::stats/min merged)
          max    (::stats/max merged)]
      (list `s/and spec `(fn [~'x] (<= ~min ~'x ~max))))
    spec))

(defn- summarize-leaf [{:keys [::stats/pred-map
                               ::stats/sample-count
                               ::stats/distinct-values
                               ::stats/hit-distinct-values-limit] :as stats}
                       spec-name
                       options]
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
          (->> (pred->form (ffirst pred-map))
               (restrict-range stats spec-name options)
               (wrap-nilable nilable?))

          (> (count pred-map) 1)
          (concat
           (list `s/or)
           (->> (map first pred-map)
                (map (juxt pred->name
                           (comp (partial wrap-nilable nilable?)
                                 (partial restrict-range stats spec-name options)
                                 pred->form)))
                ;;(remove (comp nil? first))
                (sort-by first)
                (apply concat))))))

(defn- qualified-key? [k] (some? (namespace k)))
(defn- qualify-key [k ns] (if (namespace k) k (keyword (str ns) (name k))))

(declare summarize-stats*)
(defn- summarize-non-keyword-map [keys-stats ns spec-name options]
  (list `s/map-of
        (summarize-stats* (stats/collect (keys keys-stats)) ns spec-name options)
        (summarize-stats* (reduce merge-stats (vals keys-stats)) ns spec-name options)))

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
                                    ::stats/mixed-sample-count] :as map-stats} ns spec-name options]
  (let [summaries
        (cond-> []
          (and empty-sample-count (not keyword-sample-count))
          (conj [:empty `(s/and empty? map?)])

          non-keyword-sample-count
          (conj [:non-keyword-map (summarize-non-keyword-map (non-keyword-map-stats keys) ns spec-name options)])

          keyword-sample-count
          (conj [:keyword-map (summarize-keys (keyword-map-stats keys)
                                              (+ (or empty-sample-count 0) keyword-sample-count)
                                              ns)]))]
    (cond mixed-sample-count
          (list `s/and
                (summarize-keys (keyword-map-stats keys)
                                (+ mixed-sample-count
                                   (or empty-sample-count 0)
                                   (or keyword-sample-count 0)
                                   (or non-keyword-sample-count 0))
                                ns)
                (summarize-non-keyword-map keys ns spec-name options))

          (= 1 (count summaries))
          (-> summaries first second)

          :else
          (concat (list `s/or) (apply concat summaries)))))

(defn- add-kind [kind spec]
  (if (and (list? spec) kind)
    (concat spec (list :kind kind))
    spec))

(defn- summarize-coll-elements [stats spec-ns spec-name options]
  (list `s/coll-of (summarize-stats* stats spec-ns spec-name options)))

(defn- summarize-pos-elements [stats spec-ns spec-name options]
  (list
   `s/spec
   (concat
    (list `s/cat)
    (interleave
     (map #(keyword (str "el" %)) (range))
     (map #(summarize-stats* % spec-ns spec-name options)
          (vals (sort-by key stats))))))) ;;TODO extra rules for optional elements

(defn- summarize-stats*
  [{pred-map            ::stats/pred-map
    map-stats           ::stats/map
    sample-count        ::stats/sample-count
    elements-coll-stats ::stats/elements-coll
    elements-pos-stats  ::stats/elements-pos
    elements-set-stats  ::stats/elements-set
    :as                 stats}
   spec-ns
   spec-name
   options]
  (let [nilable?   (get-in stats [::stats/pred-map nil?])
        leaf-stats (cond-> stats
                     map-stats           (update ::stats/pred-map dissoc map?)
                     elements-coll-stats (update ::stats/pred-map dissoc sequential?)
                     elements-pos-stats  (update ::stats/pred-map dissoc sequential?))
        summaries
        (remove
         (comp nil? second)
         [(when map-stats [:map (wrap-nilable nilable? (summarize-map-stats map-stats spec-ns spec-name options))])
          (when elements-coll-stats
            [:collection
             (->> (summarize-coll-elements elements-coll-stats spec-ns spec-name options)
                  (wrap-nilable nilable?))])
          (when elements-pos-stats [:cat (summarize-pos-elements elements-pos-stats spec-ns spec-name options)])
          (when elements-set-stats
            [:set
             (->> (summarize-coll-elements elements-set-stats spec-ns spec-name options)
                  (add-kind `set?)
                  (wrap-nilable nilable?))])
          [:simple (let [s (summarize-leaf leaf-stats spec-name options)]
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

(defn summarize-stats
  ([stats spec-name]
   (summarize-stats stats spec-name {}))
  ([stats spec-name options]
   (let [spec-ns               (namespace spec-name)
         {:keys [order stats]} (flatten-stats stats spec-name)
         specs
         (->> (map #(vector % (get stats %)) (distinct order))
              (map (fn [[stat-name stats]]
                     (let [stat-name (if (namespace stat-name)
                                       stat-name
                                       (keyword spec-ns (name stat-name)))]
                       [stat-name (rewrite/maybe-promote-spec (summarize-stats* stats spec-ns stat-name options))])))
              (map (fn [[spec-name spec]]
                     (list `s/def spec-name spec))))]
     (-> specs
         rewrite/all-nilable-or
         rewrite/known-names))))
(s/fdef summarize-stats
        :args (s/cat :stats (s/nilable ::stats/stats)
                     :spec-name qualified-keyword?
                     :options (s/* ::options)))

(defn infer-specs
  ([data spec-name]
   (infer-specs data spec-name {}))
  ([data spec-name options]
   (when-not (namespace spec-name)
     (throw
      (ex-info (str "invalid spec-name " spec-name " - should be fully-qualified keyword")
               {:spec-name spec-name})))
   (let [stats (stats/collect data options)]
     (s/valid? ::stats/stats stats)
     (summarize-stats stats spec-name options))))
(s/fdef infer-specs
        :args (s/cat :data (s/coll-of any?)
                     :spec-name qualified-keyword?
                     :options (s/* ::options)))

(defn unqualify-spec [spec domain-ns clojure-spec-ns]
  (let [domain-ns       (when domain-ns (str domain-ns))
        clojure-spec-ns (when clojure-spec-ns (str clojure-spec-ns))]
    (walk/postwalk
     (fn [x]
       (cond (and clojure-spec-ns (symbol? x)
                  #?(:clj (= "clojure.spec.alpha" (namespace x))
                     :cljs (or (= "clojure.spec.alpha" (namespace x))
                               (= "cljs.spec.alpha" (namespace x)))))
               (symbol clojure-spec-ns (name x))
             (and (symbol? x)
                  #?(:clj (= "clojure.core" (namespace x))
                     :cljs (= "cljs.core" (namespace x))))
               (symbol (name x))
             (and domain-ns (keyword? x) (= domain-ns (namespace x)))
               (symbol (str "::" (name x))) ;;nasty hack to get the printer to print ::foo
             (and domain-ns (symbol? x) (= domain-ns (namespace x)))
               (-> x name symbol)
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
