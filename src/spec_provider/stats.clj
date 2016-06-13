(ns spec-provider.stats
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

(def distinct-limit 10)

(def preds
  [string?
   float?
   integer?
   keyword?
   boolean?
   sequential?
   set?
   map?])

(s/def ::distinct-values (s/* ::s/any))
(s/def ::count pos-long?)
(s/def ::pred-stats
  (s/keys
   :req [::count]
   :opt [::min ::max ::min-length ::max-length]))
(s/def ::pred-map (s/map-of ::s/any ::pred-stats))
(s/def ::name string?)

(s/def ::keys (s/map-of ::s/any ::stats))
(s/def ::elements (s/* ::stats))

(s/def ::stats
  (s/keys
   :req [::distinct-values ::count ::pred-map]
   :opt [::name ::keys ::elements]))


(defn- safe-inc [x] (if x (inc x) 1))
(defn- safe-set-conj [s x] (if s (conj s x) #{x}))
(s/fdef safe-set-conj :args (s/cat :set set? :value ::s/any))

(defn update-pred-stats [pred-stats x]
  (let [s (update pred-stats ::count safe-inc)
        number (number? x)
        counted (or (counted? x) (string? x))
        c (when counted (count x))]
    (cond-> s
      (and number (< x (or (:min s) Long/MAX_VALUE))) (assoc :min x)
      (and number (> x (or (:max s) Long/MIN_VALUE))) (assoc :max x)
      (and c (< c (or (:min-length s) Long/MAX_VALUE))) (assoc :min-length c)
      (and c (> c (or (:max-length s) Long/MIN_VALUE))) (assoc :max-length c))))
(s/fdef update-pred-stats
        :args (s/cat :pred-stats ::pred-stats :value ::s/any)
        :ret ::pred-stats)

(defn update-pred-map [pred-map x]
  (reduce
   (fn [pred-map pred]
     (if-not (pred x)
       pred-map
       (update pred-map pred update-pred-stats x)))
   pred-map preds))
(s/fdef update-pred-stats
        :args (s/cat :pred-map ::pred-map :value ::s/any)
        :ret ::pred-map)

(declare update-stats)
(defn update-keys-stats [keys-stats x]
  (if-not (map? x)
    keys-stats
    (reduce-kv
     (fn [stats k v]
       (update stats k update-stats v))
     keys-stats x)))
(s/fdef update-keys-stats
        :args (s/cat :keys ::keys :value ::s/any)
        :ret ::keys)

(defn empty-stats []
  {::distinct-values #{}
   ::count 0
   ::pred-map {}})
(s/fdef empty-stats :ret ::stats)

(defn update-stats [stats x]
  (-> (or stats (empty-stats))
      (update ::count safe-inc)
      (update ::pred-map update-pred-map x)
      (cond->
          (map? x)
            (update ::keys update-keys-stats x) ;;TODO ::elements
          (and (not (coll? x)) (-> stats ::distinct-values count (< distinct-limit)))
            (update ::distinct-values safe-set-conj x))))
(s/fdef update-stats
        :args (s/cat :stats (s/nilable ::stats) :value ::s/any)
        :ret ::stats)


(comment
  (require '[spec-provider.person-spec :as p]
           '[clojure.pprint :refer [pprint]])
  (pprint (reduce update-stats nil (gen/sample (s/gen ::p/person) 100)))

  (pprint (derive-spec (gen/sample (s/gen ::p/person) 100))))
