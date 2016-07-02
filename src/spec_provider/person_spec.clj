(ns spec-provider.person-spec
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [spec-provider.core :as provider]
            [spec-provider.stats :as stats]
            [clojure.pprint :refer [pprint]]))

(s/def ::id (s/or :numeric pos-int? :string string?))
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
          :opt-un [::phone-number]))

(comment
  > (provider/derive-spec (gen/sample (s/gen integer?) 1000))
  > (provider/derive-spec (gen/sample (s/gen (coll-of integer?)) 1000))


  > (pprint (reduce stats/update-stats nil (gen/sample (s/gen ::person) 100)))
  > (pprint (provider/derive-spec (gen/sample (s/gen ::person) 100)))

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
