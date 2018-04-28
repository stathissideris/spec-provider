(ns spec-provider.provider-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [spec-provider.provider :as pr]
            [spec-provider.person-spec :as person]
            [spec-provider.stats :as stats]))

#?(:clj  (def int-pred? 'clojure.core/integer?))
#?(:cljs (def int-pred? '(clojure.spec.alpha/or :double cljs.core/double? :integer cljs.core/integer?))) ;;cljs has a different numerical tower

(deftest summarize-stats-test
  (is (= `((clojure.spec.alpha/def :domain/foo ~int-pred?))
         (pr/summarize-stats
          #::stats
          {:sample-count 1
           :pred-map {~int-pred?
                      #::stats
                      {:sample-count 1
                       :min 6
                       :max 6}}
           :distinct-values #{6}}
          :domain/foo))))

(deftest infer-specs-test

  (is (= `((clojure.spec.alpha/def :foo/bar ~int-pred?)
           (clojure.spec.alpha/def :foo/foo ~int-pred?)
           (clojure.spec.alpha/def
             :foo/stuff
             (clojure.spec.alpha/or
              :map (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])
              :simple ~int-pred?)))
         (pr/infer-specs [1 2 {:foo 3 :bar 4}] :foo/stuff)))

  (is (= `((clojure.spec.alpha/def :foo/bar ~int-pred?)
           (clojure.spec.alpha/def :foo/foo ~int-pred?)
           (clojure.spec.alpha/def
             :foo/vector
             (clojure.spec.alpha/coll-of
              (clojure.spec.alpha/or
               :map
               (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])
               :simple
               ~int-pred?))))
         (pr/infer-specs [[1 2 {:foo 3 :bar 4}]] :foo/vector)))

  (is (= `((clojure.spec.alpha/def :foo/boo ~int-pred?)
           (clojure.spec.alpha/def :foo/baz ~int-pred?)
           (clojure.spec.alpha/def
             :foo/bar
             (clojure.spec.alpha/keys :req-un [:foo/baz :foo/boo]))
           (clojure.spec.alpha/def :foo/foo ~int-pred?)
           (clojure.spec.alpha/def
             :foo/map
             (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])))
         (pr/infer-specs [{:foo 1 :bar {:baz 2 :boo 3}}] :foo/map)))

  (is (pr/infer-specs [{:a {:b [1 2]}} {:b [1]}] ::foo)) ;; issue #7
  (is (pr/infer-specs [{:a {:b [{} {}]}} {:b [{}]}] ::foo)) ;; issue #7
  (is (pr/infer-specs [{:a {:b [[] []]}} {:b [[]]}] ::foo))

  (is (pr/infer-specs (gen/sample (s/gen ~int-pred?) 1000) :foo/int))

  (is (pr/infer-specs (gen/sample (s/gen (s/coll-of ~int-pred?)) 1000) :foo/coll-of-ints))

  (is (= `((clojure.spec.alpha/def :foo/date inst?))
         (pr/infer-specs (gen/sample (s/gen inst?) 100) :foo/date)))

  (testing "map optional keys"
    (is (= '((clojure.spec.alpha/def :foo/a ~int-pred?)
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :opt-un [:foo/a])))
           (pr/infer-specs [{}
                         {}
                         {:a 1}] :foo/stuff)))
    (is (= `((clojure.spec.alpha/def :foo/a ~int-pred?)
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :opt-un [:foo/a])))
           (pr/infer-specs [{:a 1}
                         {}
                         {}] :foo/stuff)))
    (is (= `((clojure.spec.alpha/def :foo/b ~int-pred?)
             (clojure.spec.alpha/def :foo/a ~int-pred?)
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :opt-un [:foo/a :foo/b])))
           (pr/infer-specs [{:a 1}
                         {:a 1}
                         {:b 1}] :foo/stuff)))
    (is (= `((clojure.spec.alpha/def :foo/b ~int-pred?)
             (clojure.spec.alpha/def :foo/a ~int-pred?)
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :req-un [:foo/b] :opt-un [:foo/a])))
           (pr/infer-specs [{:a 1 :b 1}
                         {:a 1 :b 1}
                         {:b 1}] :foo/stuff)))
    (is (= `((clojure.spec.alpha/def :foo/b ~int-pred?)
             (clojure.spec.alpha/def :foo/a ~int-pred?)
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :req-un [:foo/a :foo/b])))
           (pr/infer-specs [{:a 1 :b 1}
                         {:a 2 :b 2}
                         {:a 3 :b 3}] :foo/stuff)))
    (is (= `((clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/and empty? map?)))
           (pr/infer-specs [{}] :foo/stuff))))

  (testing "maps that don't have keywords as keys"
    (is (= `((clojure.spec.alpha/def
               :foo/stuff
               (clojure.spec.alpha/map-of string? ~int-pred?)))
           (pr/infer-specs [{"a" 3}
                         {"b" 4}] :foo/stuff)))
    (is (= `((clojure.spec.alpha/def
               :foo/stuff
               (clojure.spec.alpha/map-of
                (clojure.spec.alpha/coll-of
                 (clojure.spec.alpha/nilable
                  (clojure.spec.alpha/or :integer ~int-pred? :keyword keyword?)))
                ~int-pred?)))
           (pr/infer-specs [{[8 :a] 3}
                         {[nil :b] 4}] :foo/stuff)))
    (testing "- mixed"
      (is (= `((clojure.spec.alpha/def :foo/a ~int-pred?)
               (clojure.spec.alpha/def
                 :foo/stuff
                 (clojure.spec.alpha/or
                  :non-keyword-map
                  (clojure.spec.alpha/map-of ~int-pred? ~int-pred?)
                  :keyword-map
                  (clojure.spec.alpha/keys :req-un [:foo/a]))))
             (pr/infer-specs [{:a 4}
                           {4 4}] :foo/stuff)))
      (is (= `((clojure.spec.alpha/def :foo/b ~int-pred?)
               (clojure.spec.alpha/def :foo/a ~int-pred?)
               (clojure.spec.alpha/def
                 :foo/stuff
                 (clojure.spec.alpha/and
                  (clojure.spec.alpha/keys :opt-un [:foo/a :foo/b])
                  (clojure.spec.alpha/map-of
                   (clojure.spec.alpha/or :integer ~int-pred? :keyword keyword?)
                   ~int-pred?))))
             (pr/infer-specs [{:a 4, 4 4}
                           {:b 10, 4 10}] :foo/stuff)))
      (is (= `((clojure.spec.alpha/def :foo/b ~int-pred?)
               (clojure.spec.alpha/def :foo/a ~int-pred?)
               (clojure.spec.alpha/def
                 :foo/stuff
                 (clojure.spec.alpha/and
                  (clojure.spec.alpha/keys :opt-un [:foo/a :foo/b])
                  (clojure.spec.alpha/map-of
                   (clojure.spec.alpha/or :keyword keyword? :string string?)
                   ~int-pred?))))
             (pr/infer-specs [{"foo" 0}
                           {:a 4, "4" 4}
                           {:a 5 :b 10, "4" 10}] :foo/stuff)))
      (is (= '((clojure.spec.alpha/def :foo/b ~int-pred?)
               (clojure.spec.alpha/def :foo/a ~int-pred?)
               (clojure.spec.alpha/def
                 :foo/stuff
                 (clojure.spec.alpha/and
                  (clojure.spec.alpha/keys :opt-un [:foo/a :foo/b])
                  (clojure.spec.alpha/map-of
                   (clojure.spec.alpha/or :keyword keyword? :string string?)
                   ~int-pred?))))
             (pr/infer-specs [{}
                           {"foo" 0}
                           {:a 4, "4" 4}
                           {:a 5 :b 10, "4" 10}] :foo/stuff)))
      (is (= `((clojure.spec.alpha/def :foo/b ~int-pred?)
               (clojure.spec.alpha/def :foo/a ~int-pred?)
               (clojure.spec.alpha/def
                 :foo/stuff
                 (clojure.spec.alpha/and
                  (clojure.spec.alpha/keys :opt-un [:foo/a :foo/b])
                  (clojure.spec.alpha/map-of
                   (clojure.spec.alpha/or :keyword keyword? :string string?)
                   ~int-pred?))))
             (pr/infer-specs [{}
                           {:a 4, "4" 4}
                           {:a 5 :b 10, "4" 10}] :foo/stuff))))

    ;;TODO this case does not work as expected (a is not promoted to top-level as a named spec):
    ;; (pr/infer-specs [{{:a 4} 3}
    ;;               {{:a 8} 4}] ::foo)
    )

  (testing "positional (cat) specs"
    (is (= `((clojure.spec.alpha/def :foo/bar ~int-pred?)
             (clojure.spec.alpha/def :foo/foo ~int-pred?)
             (clojure.spec.alpha/def :foo/vector
               (clojure.spec.alpha/cat
                :el0 ~int-pred?
                :el1 ~int-pred?
                :el2 (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo]))))
           (pr/infer-specs [[1 2 {:foo 3 :bar 4}]]
                        :foo/vector
                        #::stats{:positional true})))
    (is (= `((clojure.spec.alpha/def :foo/bar ~int-pred?)
             (clojure.spec.alpha/def :foo/foo ~int-pred?)
             (clojure.spec.alpha/def
               :foo/vector
               (clojure.spec.alpha/cat
                :el0 ~int-pred?
                :el1 ~int-pred?
                :el2 (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])
                :el3 (clojure.spec.alpha/spec
                      (clojure.spec.alpha/cat
                       :el0 double?
                       :el1 double?
                       :el2 double?)))))
           (pr/infer-specs [[1 2 {:foo 3 :bar 4} [1.2 5.4 3.0]]]
                        :foo/vector
                        #::stats{:positional true}))))

  (testing "order of or"
    (is (= `((clojure.spec.alpha/def
               :spec-provider.provider-test/stuff
               (clojure.spec.alpha/or
                :map (clojure.spec.alpha/and clojure.core/empty? clojure.core/map?)
                :set (clojure.spec.alpha/coll-of clojure.core/any? :kind clojure.core/set?)
                :simple
                (clojure.spec.alpha/or
                 :boolean boolean?
                 :double  double?
                 :float   float?
                 :integer ~int-pred?
                 :keyword keyword?))))
           (pr/infer-specs [:k true (double 1) false (float 3) #{} {} (int 5)] ::stuff))))

  (testing "issue #1 - coll-of overrides everything"
    (is (= `((clojure.spec.alpha/def
               :spec-provider.provider-test/stuff
               (clojure.spec.alpha/or
                :collection (clojure.spec.alpha/coll-of ~int-pred?)
                :map (clojure.spec.alpha/and clojure.core/empty? clojure.core/map?)
                :set (clojure.spec.alpha/coll-of clojure.core/any? :kind clojure.core/set?)
                :simple
                (clojure.spec.alpha/or
                 :boolean boolean?
                 :double  double?
                 :float   float?
                 :integer ~int-pred?
                 :keyword keyword?))))
           (pr/infer-specs [:k true (double 1) false '(1 2 3) (float 3) #{} {} (int 5)] ::stuff))))

  (testing "nilable"
    (is (= `((clojure.spec.alpha/def ::a
               (clojure.spec.alpha/nilable ~int-pred?))
             (clojure.spec.alpha/def ::foo
               (clojure.spec.alpha/keys :req-un [::a])))
           (pr/infer-specs [{:a 5} {:a nil}] ::foo)))
    (is (= `((clojure.spec.alpha/def ::foo (clojure.spec.alpha/nilable ~int-pred?)))
           (pr/infer-specs [1 2 nil] ::foo)))
    (is (= `((clojure.spec.alpha/def ::foo (clojure.spec.alpha/coll-of (clojure.spec.alpha/nilable ~int-pred?))))
           (pr/infer-specs [[1] [2] [nil]] ::foo)))
    (is (= `((clojure.spec.alpha/def ::foo (clojure.spec.alpha/nilable (clojure.spec.alpha/coll-of ~int-pred?))))
           (pr/infer-specs [[1] [2] nil] ::foo)))
    (is (= `((clojure.spec.alpha/def ::a ~int-pred?)
             (clojure.spec.alpha/def
               ::foo
               (clojure.spec.alpha/nilable (clojure.spec.alpha/keys :req [::a]))))
           (pr/infer-specs [{::a 9} {::a 10} nil] ::foo)))
    (testing " with positional"
      (is (= `((clojure.spec.alpha/def :foo/bar ~int-pred?)
               (clojure.spec.alpha/def :foo/foo ~int-pred?)
               (clojure.spec.alpha/def
                 :foo/vector
                 (clojure.spec.alpha/cat
                  :el0 (clojure.spec.alpha/nilable ~int-pred?)
                  :el1 ~int-pred?
                  :el2 (clojure.spec.alpha/nilable (clojure.spec.alpha/keys :req-un [:foo/bar :foo/foo])))))
             (pr/infer-specs [[1 2 {:foo 3 :bar 4}]
                           [nil 2 {:foo 3 :bar 4}]
                           [1 2 nil]]
                          :foo/vector
                          #::stats{:positional true})))))

  (testing "do I know you from somewhere?"
    (is (= `((clojure.spec.alpha/def :foo/zz ~int-pred?)
             (clojure.spec.alpha/def :foo/b
               (clojure.spec.alpha/nilable (clojure.spec.alpha/keys :req-un [:foo/zz])))
             (clojure.spec.alpha/def :foo/a (clojure.spec.alpha/coll-of :foo/b))
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :req-un [:foo/a :foo/b])))
           (pr/infer-specs [{:a [{:zz 1}] :b {:zz 2}}
                         {:a [{:zz 1} {:zz 4} nil] :b nil}] :foo/stuff))))

  (testing "sets"
    (is (= `((clojure.spec.alpha/def :foo/a (clojure.spec.alpha/coll-of keyword? :kind clojure.core/set?))
             (clojure.spec.alpha/def :foo/b (clojure.spec.alpha/coll-of keyword? :kind clojure.core/set?))
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :opt-un [:foo/a :foo/b])))
           (pr/infer-specs
            [{:b #{:a}}
             {:b #{:b}}
             {:a #{:c}}]
            :foo/stuff)))
    (is (= `((clojure.spec.alpha/def
               :foo/b (clojure.spec.alpha/coll-of string? :kind clojure.core/set?))
             (clojure.spec.alpha/def :foo/a (clojure.spec.alpha/keys :req-un [:foo/b]))
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :opt-un [:foo/a :foo/b])))
           (pr/infer-specs
            [{:a {:b #{"string 1" "string 2"}}}
             {:b #{"string 3"}}]
            :foo/stuff)))
    (is (= `((clojure.spec.alpha/def
               :foo/b (clojure.spec.alpha/coll-of
                       (clojure.spec.alpha/or :keyword keyword?
                                              :string string?)
                       :kind clojure.core/set?))
             (clojure.spec.alpha/def :foo/a (clojure.spec.alpha/keys :req-un [:foo/b]))
             (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :opt-un [:foo/a :foo/b])))
           (pr/infer-specs
            [{:a {:b #{:a "string 2"}}}
             {:b #{"string 3"}}] :foo/stuff)))

    (testing "- nilable"
      (is (= `((clojure.spec.alpha/def
                 :foo/a
                 (clojure.spec.alpha/nilable
                  (clojure.spec.alpha/coll-of ~int-pred? :kind clojure.core/set?)))
               (clojure.spec.alpha/def :foo/stuff (clojure.spec.alpha/keys :req-un [:foo/a])))
             (pr/infer-specs [{:a #{1}} {:a nil}] :foo/stuff)))))

  (testing "rewrite/all-nilable-or"
    (is (= `((clojure.spec.alpha/def
               :foo/stuff
               (clojure.spec.alpha/nilable
                (clojure.spec.alpha/or
                 :integer ~int-pred?
                 :keyword keyword?
                 :string  string?))))
           (pr/infer-specs [1 :a "string" nil] :foo/stuff))))

  (testing "numerical ranges"
    (testing "- for all keys"
     (is (= `((clojure.spec.alpha/def :spec-provider.provider-test/bar
                (clojure.spec.alpha/and ~int-pred? (clojure.core/fn [x] (clojure.core/<= -400 x 400))))
              (clojure.spec.alpha/def :spec-provider.provider-test/foo
                (clojure.spec.alpha/and ~int-pred? (clojure.core/fn [x] (clojure.core/<= 3 x 10))))
              (clojure.spec.alpha/def :spec-provider.provider-test/stuff
                (clojure.spec.alpha/keys :req-un [:spec-provider.provider-test/bar :spec-provider.provider-test/foo])))
            (pr/infer-specs [{:foo 3, :bar -400}
                          {:foo 3, :bar 4}
                          {:foo 3, :bar 4}
                          {:foo 3, :bar 4}
                          {:foo 10, :bar 400}] ::stuff {::pr/range true}))))
    (testing "- for specific keys"
      (is (= `((clojure.spec.alpha/def :spec-provider.provider-test/bar ~int-pred?)
               (clojure.spec.alpha/def :spec-provider.provider-test/foo
                 (clojure.spec.alpha/and ~int-pred? (clojure.core/fn [x] (clojure.core/<= 3 x 10))))
               (clojure.spec.alpha/def :spec-provider.provider-test/stuff
                 (clojure.spec.alpha/keys :req-un [:spec-provider.provider-test/bar :spec-provider.provider-test/foo])))
             (pr/infer-specs [{:foo 3, :bar -400}
                           {:foo 3, :bar 4}
                           {:foo 3, :bar 4}
                           {:foo 3, :bar 4}
                           {:foo 10, :bar 400}] ::stuff {::pr/range #{::foo}}))))
    (testing "- for map-of"
      (is (= `((clojure.spec.alpha/def :spec-provider.provider-test/stuff
                 (clojure.spec.alpha/map-of
                  (clojure.spec.alpha/and ~int-pred? (clojure.core/fn [x] (clojure.core/<= 1 x 4)))
                  (clojure.spec.alpha/and ~int-pred? (clojure.core/fn [x] (clojure.core/<= 100 x 1000))))))
             (pr/infer-specs [{1 100 2 200}
                           {1 200 4 1000}] ::stuff {::pr/range true}))))
    (testing "- ignored when the key is not numerical"
      (is (= `((clojure.spec.alpha/def :spec-provider.provider-test/bar string?)
               (clojure.spec.alpha/def :spec-provider.provider-test/foo ~int-pred?)
               (clojure.spec.alpha/def :spec-provider.provider-test/stuff
                 (clojure.spec.alpha/keys :req-un [:spec-provider.provider-test/bar :spec-provider.provider-test/foo])))
             (pr/infer-specs [{:foo 3, :bar "dwdw"}
                           {:foo 3, :bar "dwdw"}
                           {:foo 3, :bar "dqdw"}
                           {:foo 3, :bar "dwdw"}
                           {:foo 10, :bar "dwdw"}] ::stuff {::pr/range #{::bar}}))))
    (testing "- with mixed values"
      (is (= `((clojure.spec.alpha/def :spec-provider.provider-test/bar
                 (clojure.spec.alpha/or
                  :integer (clojure.spec.alpha/and ~int-pred? (clojure.core/fn [x] (clojure.core/<= 1 x 100)))
                  :string string?))
               (clojure.spec.alpha/def :spec-provider.provider-test/foo
                 (clojure.spec.alpha/and ~int-pred? (clojure.core/fn [x] (clojure.core/<= 3 x 10))))
               (clojure.spec.alpha/def :spec-provider.provider-test/stuff
                 (clojure.spec.alpha/keys :req-un [:spec-provider.provider-test/bar :spec-provider.provider-test/foo])))
             (pr/infer-specs [{:foo 3, :bar 1}
                           {:foo 3, :bar "dwdw"}
                           {:foo 3, :bar "dqdw"}
                           {:foo 3, :bar 100}
                           {:foo 10, :bar "dwdw"}] ::stuff {::pr/range true}))))))

(deftest person-spec-inference-test
  (let [persons (gen/sample (s/gen ::person/person) 100)]
    (is (= (into
            #{}
            `((clojure.spec.alpha/def :person/codes (clojure.spec.alpha/coll-of keyword?))
              (clojure.spec.alpha/def :person/bank-balance clojure.core/decimal?)
              (clojure.spec.alpha/def :person/phone-number string?)
              (clojure.spec.alpha/def :person/street-number ~int-pred?)
              (clojure.spec.alpha/def :person/country string?)
              (clojure.spec.alpha/def :person/city string?)
              (clojure.spec.alpha/def :person/street string?)
              (clojure.spec.alpha/def
                :person/address
                (clojure.spec.alpha/keys :req-un [:person/city :person/country :person/street] :opt-un [:person/street-number]))
              (clojure.spec.alpha/def :person/age ~int-pred?)
              (clojure.spec.alpha/def :person/k (clojure.spec.alpha/nilable keyword?))
              (clojure.spec.alpha/def :person/surname string?)
              (clojure.spec.alpha/def :person/first-name string?)
              (clojure.spec.alpha/def :person/id (clojure.spec.alpha/or :integer ~int-pred? :string string?))
              (clojure.spec.alpha/def :person/role #{:programmer :designer})
              (clojure.spec.alpha/def
                :person/person
                (clojure.spec.alpha/keys
                 :req
                 [:person/role]
                 :req-un
                 [:person/address :person/age :person/bank-balance :person/first-name :person/id :person/k :person/surname]
                 :opt-un
                 [:person/codes :person/phone-number]))))
           (set (pr/infer-specs persons :person/person))))))

(deftest person-spec-inference-with-merging-test
  (let [persons (map person/add-inconsistent-id
                     (gen/sample (s/gen ::person/person) 100))]
    (is (= (into
            #{}
            `((clojure.spec.alpha/def :person/codes (clojure.spec.alpha/coll-of keyword?))
              (clojure.spec.alpha/def :person/bank-balance clojure.core/decimal?)
              (clojure.spec.alpha/def :person/phone-number string?)
              (clojure.spec.alpha/def :person/id (clojure.spec.alpha/or :integer ~int-pred? :keyword keyword? :string string?))
              (clojure.spec.alpha/def :person/street-number ~int-pred?)
              (clojure.spec.alpha/def :person/country string?)
              (clojure.spec.alpha/def :person/city string?)
              (clojure.spec.alpha/def :person/street string?)
              (clojure.spec.alpha/def
                :person/address
                (clojure.spec.alpha/keys
                 :req-un [:person/city :person/country :person/id :person/street]
                 :opt-un [:person/street-number]))
              (clojure.spec.alpha/def :person/age ~int-pred?)
              (clojure.spec.alpha/def :person/k (clojure.spec.alpha/nilable keyword?))
              (clojure.spec.alpha/def :person/surname string?)
              (clojure.spec.alpha/def :person/first-name string?)
              (clojure.spec.alpha/def :person/role #{:programmer :designer})
              (clojure.spec.alpha/def
                :person/person
                (clojure.spec.alpha/keys
                 :req [:person/role]
                 :req-un [:person/address :person/age :person/bank-balance :person/first-name :person/id :person/k :person/surname]
                 :opt-un [:person/codes :person/phone-number]))))
           (set (pr/infer-specs persons :person/person))))))

(deftest pprint-specs-test
  (let [specs `[(clojure.spec.alpha/def :person/id (clojure.spec.alpha/or :numeric pos-int? :string string?))
                (clojure.spec.alpha/def :person/codes (clojure.spec.alpha/coll-of keyword? :max-gen 5))
                (clojure.spec.alpha/def :person/first-name string?)
                (clojure.spec.alpha/def :person/surname string?)
                (clojure.spec.alpha/def :person/k keyword?)
                (clojure.spec.alpha/def :person/bank-balance clojure.core/decimal?)]]
    (is (= (str "(s/def ::id (s/or :numeric pos-int? :string string?))\n"
                "(s/def ::codes (s/coll-of keyword? :max-gen 5))\n"
                "(s/def ::first-name string?)\n"
                "(s/def ::surname string?)\n"
                "(s/def ::k keyword?)\n"
                "(s/def ::bank-balance decimal?)\n")
           (with-out-str
             (pr/pprint-specs specs 'person 's))))))
