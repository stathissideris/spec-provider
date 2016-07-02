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
  > (pprint (provider/derive-spec (gen/sample (s/gen ::person) 100) :person/person))
  > (pprint
     (provider/prettify-spec
      (provider/derive-spec (gen/sample (s/gen ::person) 100) :person/person)))

  ;;produces:
  ((clojure.spec/def :person/phone-number string?)
   (clojure.spec/def :person/street-number integer?)
   (clojure.spec/def :person/country string?)
   (clojure.spec/def :person/city string?)
   (clojure.spec/def :person/street string?)
   (clojure.spec/def
     :person/address
     (clojure.spec/keys
      :un-req
      [:person/street :person/city :person/country]
      :un-opt
      [:person/street-number]))
   (clojure.spec/def :person/role #{:programmer :designer})
   (clojure.spec/def :person/age integer?)
   (clojure.spec/def :person/k keyword?)
   (clojure.spec/def :person/surname string?)
   (clojure.spec/def :person/first-name string?)
   (clojure.spec/def
     :person/id
     (clojure.spec/or :integer integer? :string string?))
   (clojure.spec/def
     :person/person
     (clojure.spec/keys
      :un-req
      [:person/id
       :person/first-name
       :person/surname
       :person/k
       :person/age
       :person/role
       :person/address]
      :un-opt
      [:person/phone-number])))

  ;;or prettier:

  > (pprint
     (provider/unqualify-specs
      (provider/derive-spec (gen/sample (s/gen ::person) 100) :person/person)
      'person 's))

  ((s/def ::phone-number string?)
   (s/def ::street-number integer?)
   (s/def ::country string?)
   (s/def ::city string?)
   (s/def ::street string?)
   (s/def
     ::address
     (s/keys
      :un-req
      [::street ::city ::country]
      :un-opt
      [::street-number]))
   (s/def ::role #{:programmer :designer})
   (s/def ::age integer?)
   (s/def ::k keyword?)
   (s/def ::surname string?)
   (s/def ::first-name string?)
   (s/def ::id (s/or :integer integer? :string string?))
   (s/def
     ::person
     (s/keys
      :un-req
      [::id ::first-name ::surname ::k ::age ::role ::address]
      :un-opt
      [::phone-number]))))
