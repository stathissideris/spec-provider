(ns spec-provider.stats
  (:refer-clojure :exclude [float?])
  (:require [clojure.spec.alpha :as s]))

(def default-options
  {::distinct-limit   10
   ::coll-limit       101
   ::positional       false
   ::positional-limit 100})
(s/def ::distinct-limit pos-int?)
(s/def ::coll-limit pos-int?)
(s/def ::positional boolean?)
(s/def ::positional-limit pos-int?)
(s/def ::stats-options
  (s/keys :opt-un [::distinct-limit ::coll-limit ::positional ::positional-limit]))

(defn float? [x]
  (and (clojure.core/float? x)
       (not (double? x))))

(def none-of-the-above?
  (complement
   (some-fn
    nil?
    string?
    double?
    float?
    integer?
    keyword?
    boolean?
    sequential?
    set?
    map?
    symbol?)))

(def ^:dynamic preds
  [nil?
   string?
   double?
   float?
   integer?
   keyword?
   boolean?
   sequential?
   set?
   map?
   symbol?
   none-of-the-above?])

(s/def ::distinct-values (s/* any?))
(s/def ::sample-count nat-int?)
(s/def ::min number?)
(s/def ::max number?)
(s/def ::min-length nat-int?)
(s/def ::max-length nat-int?)
(s/def ::pred-stats
  (s/keys
   :req [::sample-count]
   :opt [::min ::max ::min-length ::max-length]))
(s/def ::pred-map (s/map-of (set preds) ::pred-stats))
(s/def ::name string?)

(s/def ::keys (s/nilable (s/map-of any? ::stats))) ;;nilable because map may be empty
(s/def ::key-preds ::pred-map)
(s/def ::value-preds ::pred-map)
(s/def ::empty-sample-count nat-int?)
(s/def ::keyword-sample-count nat-int?)
(s/def ::non-keyword-sample-count nat-int?)
(s/def ::mixed-sample-count nat-int?)
(s/def ::map
  (s/keys
   :req [::sample-count
         ::keys
         ::empty-sample-count
         ::keyword-sample-count
         ::non-keyword-sample-count
         ::mixed-sample-count]))
(s/def ::elements-pos (s/map-of nat-int? ::stats))

(s/def ::hit-distinct-values-limit boolean?)
(s/def ::hit-key-size-limit boolean?)
(s/def ::stats
  (s/keys
   :req [::sample-count ::pred-map ::distinct-values]
   :opt [::name ::keys ::elements-pos ::elements-coll
         ::hit-distinct-values-limit
         ::hit-key-size-limit]))
(s/def ::elements-coll (s/nilable ::stats))


(defn- safe-inc [x] (if x (inc x) 1))
(defn- safe-set-conj [s x] (if s (conj s x) #{x}))
(s/fdef safe-set-conj :args (s/cat :set set? :value any?))

(defn update-pred-stats [pred-stats x]
  (let [s (update pred-stats ::sample-count safe-inc)
        number (number? x)
        counted (or (counted? x) (string? x))
        c (when counted (count x))]
    (cond-> s
      (and number (< x (or (::min s) Long/MAX_VALUE))) (assoc ::min x)
      (and number (> x (or (::max s) Long/MIN_VALUE))) (assoc ::max x)
      (and c (< c (or (::min-length s) Long/MAX_VALUE))) (assoc ::min-length c)
      (and c (> c (or (::max-length s) Long/MIN_VALUE))) (assoc ::max-length c))))
(s/fdef update-pred-stats
        :args (s/cat :pred-stats (s/nilable ::pred-stats) :value any?)
        :ret ::pred-stats)

(defn update-pred-map [pred-map x]
  (reduce
   (fn [pred-map pred]
     (if-not (pred x)
       pred-map
       (update pred-map pred update-pred-stats x)))
   pred-map preds))
(s/fdef update-pred-map
        :args (s/cat :pred-map (s/nilable ::pred-map) :value any?)
        :ret ::pred-map)

(declare update-stats)
(defn update-keys-stats [keys-stats x options]
  (cond (not (map? x))
        keys-stats

        (empty? x)
        keys-stats

        :else
        (reduce-kv
         (fn [stats k v]
           (update stats k update-stats v options))
         keys-stats x)))
(s/fdef update-keys-stats
        :args (s/cat :keys (s/nilable ::keys) :value any? :options ::stats-options)
        :ret ::keys)

(defn- keyword-map? [x]
  (and (map? x)
       (every? keyword? (keys x))))

(defn- non-keyword-map? [x]
  (and (map? x)
       (every? (complement keyword?) (keys x))))

(defn- map-type [m]
  (cond (empty? m)           ::empty-sample-count
        (keyword-map? m)     ::keyword-sample-count
        (non-keyword-map? m) ::non-keyword-sample-count
        :else                ::mixed-sample-count))

(defn update-map-stats [map-stats x options]
  (-> map-stats
      (update ::sample-count safe-inc)
      (update (map-type x) safe-inc)
      (update ::keys update-keys-stats x options)))

(defn update-coll-stats [stats x {:keys [::coll-limit] :as options}]
  (cond (not (or (sequential? x) (set? x)))
        stats

        (empty? x)
        (update-stats stats ::empty options)

        :else
        (reduce
         (fn [stats element]
           (update-stats stats element options))
         stats (take coll-limit x))))

(defn update-positional-stats [stats x {:keys [::positional-limit] :as options}]
  (if-not (sequential? x)
    stats
    (let [stats (or stats {})]
      (reduce (fn [stats [idx val]]
                (update stats idx update-stats val options))
              stats (map vector (range) x)))))

(defn empty-stats []
  {::distinct-values #{}
   ::sample-count 0
   ::pred-map {}})
(s/fdef empty-stats :ret ::stats)

(defn update-stats [stats x options]
  (if (= ::empty x)
    (-> (or stats (empty-stats))
        (update ::sample-count safe-inc))
    (let [{:keys [::positional ::distinct-limit] :as options}
          (merge default-options options)]
      (-> (or stats (empty-stats))
          (update ::sample-count safe-inc)
          (update ::pred-map update-pred-map x)
          (cond->
            (map? x)
            (update ::map update-map-stats x options)
            (and positional (sequential? x))
            (update ::elements-pos update-positional-stats x options)
            (and (not positional) (sequential? x))
            (update ::elements-coll update-coll-stats x options)
            (and (not positional) (set? x))
            (update ::elements-set update-coll-stats x options)
            (and (not (coll? x)) (-> stats ::distinct-values count (< distinct-limit))) ;;TODO optimize
            (update ::distinct-values safe-set-conj x)
            (and (not (coll? x)) (-> stats ::distinct-values count (>= distinct-limit)))
            (assoc ::hit-distinct-values-limit true))))))
(s/fdef update-stats
        :args (s/cat :stats (s/nilable ::stats) :value any? :options (s/? (s/nilable ::stats-options)))
        :ret ::stats)

(defn collect
  ([data]
   (collect data {}))
  ([data options]
   (reduce (fn [stats x] (update-stats stats x options)) nil data)))
(s/fdef collect-stats
        :args (s/cat :data (s/nilable any?)
                     :options (s/? (s/nilable ::stats-options)))
        :ret ::stats)

(defn empty-sequential? [stats]
  (and (-> stats ::pred-map (get sequential?))
       (zero? (-> stats ::pred-map (get sequential?) ::min-length))
       (zero? (-> stats ::pred-map (get sequential?) ::max-length))))
