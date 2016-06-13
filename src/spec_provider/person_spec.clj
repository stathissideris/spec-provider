(ns spec-provider.person-spec
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

(s/def ::id (s/or :numeric pos-long? :string string?))
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
(s/def ::street-number pos-long?)

(s/def ::address
  (s/keys :req-un [::street ::city ::country]
          :opt-un [::street-number]))

(s/def ::person
  (s/keys :req-un [::id ::first-name ::surname ::k ::age ::role ::address]
          :opt-un [::phone-number]))
