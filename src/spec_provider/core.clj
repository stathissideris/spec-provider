(ns spec-provider.core
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

;;this means that if the count of the distinct values is less than 10%
;;of the count of total values, then the attribute is considered an
;;enumeration
(def enum-threshold 0.1)

(def preds
  [string?
   float?
   integer?
   keyword?
   boolean?
   sequential?
   set?
   map?])

(def pred->form
  {string?  'string?
   float?   'float?
   integer? 'integer?
   keyword? 'keyword?})

(def pred->name
  {string?  :string
   float?   :float
   integer? :integer
   keyword? :keyword})

(s/def ::type keyword?)

(s/def ::pred-counts (s/map-of ::s/any pos-long?))
(s/def ::distinct-values (s/* ::s/any))
(s/def ::count pos-long?)

(s/def ::attribute-stats (or (s/keys :req [::pred-counts ::distinct-values ::count])
                             (s/keys :req [::nested-map])))
(s/def ::name string?)
(s/def ::attributes (s/map-of ::s/any ::attribute-stats))

(s/def ::map-stats
  (s/and
   (s/keys :req [::type ::attributes]
           :opt [::name])
   #(= ::map (::type %))))

(s/def ::stats (s/* ::map-stats))

(defn- safe-inc [x] (if x (inc x) 1))
(defn- safe-set-conj [s x] (if s (conj s x) #{x}))

(defn assimilate-attr-types [stats v]
  (reduce
   (fn [stats pred]
     (if (pred v)
       (update stats pred safe-inc)
       stats)) stats preds))

(defn assimilate-map [stats map]
  (reduce
   (fn [stats [k v]]
     (if (map? v)
       (update-in stats [::attributes k ::nested-map] assimilate-map v)
       (-> stats
           (assoc ::type ::map)
           (update-in [::attributes k ::pred-counts] assimilate-attr-types v)
           (update-in [::attributes k ::distinct] safe-set-conj v)
           (update-in [::attributes k ::count] safe-inc))))
   stats map))

(defn summarize-attr-value [{pred-counts ::pred-counts
                             distinct-values :distinct-values
                             value-count ::count}]
  (cond (> enum-threshold
           (/ (float (count distinct-values))
              (float value-count)))
        distinct-values

        (= 1 (count pred-counts))
        (pred->form (ffirst pred-counts))

        (> (count pred-counts) 1)
        (concat
         (list 'clojure.spec/or)
         (mapcat (juxt pred->name pred->form) (map first pred-counts)))))

(defn- qualified-key? [k] (some? (namespace k)))
(defn- qualify-key [k] (keyword (str *ns*) (name k))) ;;TODO we need to pass ns as a param

(defn summarize-keys [stats]
  (let [highest-freq (apply max (map :count (vals stats)))
        extract-keys (fn [filter-fn]
                       (->> stats
                            (filter filter-fn)
                            (mapv (comp qualify-key key))
                            not-empty))
        req    (extract-keys
                (fn [[k v]] (and (qualified-key? k) (= (:count v) highest-freq))))
        opt    (extract-keys
                (fn [[k v]] (and (qualified-key? k) (< (:count v) highest-freq))))
        un-req (extract-keys
                (fn [[k v]] (and (not (qualified-key? k)) (= (:count v) highest-freq))))
        un-opt (extract-keys
                (fn [[k v]] (and (not (qualified-key? k)) (< (:count v) highest-freq))))]
    (cond-> (list 'clojure.spec/keys)
      req (concat [:req req])
      opt (concat [:opt opt])
      un-req (concat [:un-req un-req])
      un-opt (concat [:un-opt un-opt]))))

(defn derive-spec [data]
  (let [stats (reduce assimilate-map {} data)]
    (concat (map
             (fn [[k v]]
               (list `s/def (qualify-key k) (summarize-attr-value v)))
             stats)
            (list `s/def ::map1 (summarize-keys stats)))))

;;derive-spec for nested maps algo:
;; 0. assign names to all nested maps based on the key
;;    they're under
;; 1. collect all nested maps stats. Also, root map (if map)
;; 2. collect all attribute stats from all maps
;; 3. (maybe) merge the attribute stats of the ones that have the same name
;; 4. derive spec for each attribute
;; 5. derive spec for each map keyset

(s/def ::id (s/or :numeric pos-long? :string string?))
(s/def ::first-name string?)
(s/def ::surname string?)
(s/def ::k keyword?)
(s/def ::age (s/and integer? pos?))
(s/def ::role #{:programmer :designer})
(s/def ::phone-number string?)

(s/def ::street string?)
(s/def ::city string?)
(s/def ::country string?)
(s/def ::street-number pos-long?)

(s/def ::address
  (s/keys :req-un [::street ::city ::country]
          :opt-un [::street-number]))

(s/def ::person
  (s/keys :req-un [::id ::first-name ::surname ::k ::age ::role ::address]
          :opt-un [::phone-number]))

;;(s/form (s/or :numeric (s/and integer? pos?) :string string?))

(comment
  > (pprint (derive-spec (gen/sample (s/gen ::person) 100)))

  ;;produces:
  ((clojure.spec/def
     :spec-provider.core/id
     (clojure.spec/or :string string? :integer integer?))
   (clojure.spec/def :spec-provider.core/first-name string?)
   (clojure.spec/def :spec-provider.core/surname string?)
   (clojure.spec/def :spec-provider.core/k keyword?)
   (clojure.spec/def :spec-provider.core/age integer?)
   (clojure.spec/def :spec-provider.core/role #{:programmer :designer})
   (clojure.spec/def :spec-provider.core/phone-number string?)
   (clojure.spec/keys
    :un-req
    [:spec-provider.core/id
     :spec-provider.core/first-name
     :spec-provider.core/surname
     :spec-provider.core/k
     :spec-provider.core/age
     :spec-provider.core/role]
    :un-opt
    [:spec-provider.core/phone-number])))
