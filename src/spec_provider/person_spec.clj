(ns spec-provider.person-spec
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [spec-provider.provider :as provider]
            [spec-provider.stats :as stats]
            [clojure.pprint :refer [pprint]]))

(s/def ::id (s/or :numeric pos-int? :string string?))
(s/def ::codes (s/coll-of keyword? :max-gen 5))
(s/def ::first-name string?)
(s/def ::surname string?)
(s/def ::k keyword?)
(s/def ::age (s/with-gen
               (s/and integer? pos? #(<= % 130))
               #(gen/int 130)))
(s/def ::role #{:programmer :designer})
(s/def ::phone-number string?)

(s/def ::street string?)
(s/def ::city string?)
(s/def ::country string?)
(s/def ::street-number pos-int?)

(s/def ::address
  (s/keys :req-un [::street ::city ::country]
          :opt-un [::street-number]))

(s/def ::person
  (s/keys :req-un [::id ::first-name ::surname ::k ::age ::role ::address]
          :opt-un [::phone-number ::codes]))

(defn add-inconsistent-id [person]
  (if (:address person)
    (assoc-in person [:address :id] (gen/generate (gen/keyword)))
    person))

(comment
  > (provider/infer-specs (gen/sample (s/gen integer?) 1000))
  > (provider/infer-specs (gen/sample (s/gen (coll-of integer?)) 1000))


  > (pprint (reduce stats/update-stats nil (gen/sample (s/gen ::person) 100)))
  > (pprint (provider/infer-spec (gen/sample (s/gen ::person) 100) :person/person))

  > (def persons (map add-inconsistent-id (gen/sample (s/gen ::person) 100)))

  > (provider/pprint-specs
     (provider/infer-specs persons :person/person)
     'person 's)

  (s/def ::phone-number string?)
  (s/def ::codes (s/coll-of keyword?))
  (s/def ::street-number integer?)
  (s/def ::country string?)
  (s/def ::city string?)
  (s/def ::street string?)
  (s/def
    ::address
    (s/keys :req-un [::street ::city ::country] :opt-un [::street-number]))
  (s/def ::role #{:programmer :designer})
  (s/def ::age integer?)
  (s/def ::k keyword?)
  (s/def ::surname string?)
  (s/def ::first-name string?)
  (s/def ::id (s/or :integer integer? :string string?))
  (s/def
    ::person
    (s/keys
     :req-un
     [::id ::first-name ::surname ::k ::age ::role ::address]
     :opt-un
     [::codes ::phone-number])))
