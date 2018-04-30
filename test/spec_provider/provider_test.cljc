(ns spec-provider.provider-test
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [spec-provider.provider :as pr]
            [spec-provider.person-spec :as person]
            [spec-provider.stats :as stats]
            [clojure.string :as str]))

(deftest summarize-stats-test
  (is (= `((s/def :domain/foo integer?))
         (pr/summarize-stats
          #::stats
          {:sample-count 1
           :pred-map {integer?
                      #::stats
                      {:sample-count 1
                       :min 6
                       :max 6}}
           :distinct-values #{6}}
          :domain/foo))))

(deftest infer-specs-test

  (is (= `[(s/def :foo/bar integer?)
           (s/def :foo/foo integer?)
           (s/def
             :foo/stuff
             (s/or
              :map (s/keys :req-un [:foo/bar :foo/foo])
              :simple integer?))]
         (pr/infer-specs [1 2 {:foo 3 :bar 4}] :foo/stuff)))

  (is (= `[(s/def :foo/bar integer?)
           (s/def :foo/foo integer?)
           (s/def
             :foo/vector
             (s/coll-of
              (s/or
               :map
               (s/keys :req-un [:foo/bar :foo/foo])
               :simple
               integer?)))]
         (pr/infer-specs [[1 2 {:foo 3 :bar 4}]] :foo/vector)))

  (is (= `[(s/def :foo/boo integer?)
           (s/def :foo/baz integer?)
           (s/def
             :foo/bar
             (s/keys :req-un [:foo/baz :foo/boo]))
           (s/def :foo/foo integer?)
           (s/def
             :foo/map
             (s/keys :req-un [:foo/bar :foo/foo]))]
         (pr/infer-specs [{:foo 1 :bar {:baz 2 :boo 3}}] :foo/map)))

  (is (pr/infer-specs [{:a {:b [1 2]}} {:b [1]}] ::foo)) ;; issue #7
  (is (pr/infer-specs [{:a {:b [{} {}]}} {:b [{}]}] ::foo)) ;; issue #7
  (is (pr/infer-specs [{:a {:b [[] []]}} {:b [[]]}] ::foo))

  (is (pr/infer-specs (gen/sample (s/gen integer?) 1000) :foo/int))

  (is (pr/infer-specs (gen/sample (s/gen (s/coll-of integer?)) 1000) :foo/coll-of-ints))

  (is (= `[(s/def :foo/date inst?)]
         (pr/infer-specs (gen/sample (s/gen inst?) 100) :foo/date)))

  (testing "map optional keys"
    (is (= `[(s/def :foo/a integer?)
             (s/def :foo/stuff (s/keys :opt-un [:foo/a]))]
           (pr/infer-specs [{}
                         {}
                         {:a 1}] :foo/stuff)))
    (is (= `[(s/def :foo/a integer?)
             (s/def :foo/stuff (s/keys :opt-un [:foo/a]))]
           (pr/infer-specs [{:a 1}
                         {}
                         {}] :foo/stuff)))
    (is (= `[(s/def :foo/b integer?)
             (s/def :foo/a integer?)
             (s/def :foo/stuff (s/keys :opt-un [:foo/a :foo/b]))]
           (pr/infer-specs [{:a 1}
                         {:a 1}
                         {:b 1}] :foo/stuff)))
    (is (= `[(s/def :foo/b integer?)
             (s/def :foo/a integer?)
             (s/def :foo/stuff (s/keys :req-un [:foo/b] :opt-un [:foo/a]))]
           (pr/infer-specs [{:a 1 :b 1}
                         {:a 1 :b 1}
                         {:b 1}] :foo/stuff)))
    (is (= `[(s/def :foo/b integer?)
             (s/def :foo/a integer?)
             (s/def :foo/stuff (s/keys :req-un [:foo/a :foo/b]))]
           (pr/infer-specs [{:a 1 :b 1}
                         {:a 2 :b 2}
                         {:a 3 :b 3}] :foo/stuff)))
    (is (= `[(s/def :foo/stuff (s/and empty? map?))]
           (pr/infer-specs [{}] :foo/stuff))))

  (testing "maps with mixed namespaced keys"
    (is (= `[(s/def ::gen/bar clojure.core/integer?)
             (s/def ::s/foo clojure.core/integer?)
             (s/def :foobar/a clojure.core/integer?)
             (s/def :foobar/my
               (s/keys :req    [::s/foo ::gen/bar]
                       :req-un [:foobar/a]))]
           (pr/infer-specs [{:a 0 ::s/foo 1 ::gen/bar 2}
                            {:a 1 ::s/foo 10 ::gen/bar 20}
                            {:a 2 ::s/foo 100 ::gen/bar 200}] :foobar/my))))

  (testing "maps that don't have keywords as keys"
    (is (= `[(s/def
               :foo/stuff
               (s/map-of string? integer?))]
           (pr/infer-specs [{"a" 3}
                            {"b" 4}] :foo/stuff)))
    (is (= `[(s/def
               :foo/stuff
               (s/map-of
                (s/coll-of
                 (s/nilable
                  (s/or :integer integer? :keyword keyword?)))
                integer?))]
           (pr/infer-specs [{[8 :a] 3}
                            {[nil :b] 4}] :foo/stuff)))
    (testing "- mixed"
      (is (= `[(s/def :foo/a integer?)
               (s/def
                 :foo/stuff
                 (s/or
                  :non-keyword-map
                  (s/map-of integer? integer?)
                  :keyword-map
                  (s/keys :req-un [:foo/a])))]
             (pr/infer-specs [{:a 4}
                              {4 4}] :foo/stuff)))
      (is (= `[(s/def :foo/b integer?)
               (s/def :foo/a integer?)
               (s/def
                 :foo/stuff
                 (s/and
                  (s/keys :opt-un [:foo/a :foo/b])
                  (s/map-of
                   (s/or :integer integer? :keyword keyword?)
                   integer?)))]
             (pr/infer-specs [{:a 4, 4 4}
                              {:b 10, 4 10}] :foo/stuff)))
      (is (= `[(s/def :foo/b integer?)
               (s/def :foo/a integer?)
               (s/def
                 :foo/stuff
                 (s/and
                  (s/keys :opt-un [:foo/a :foo/b])
                  (s/map-of
                   (s/or :keyword keyword? :string string?)
                   integer?)))]
             (pr/infer-specs [{"foo" 0}
                              {:a 4, "4" 4}
                              {:a 5 :b 10, "4" 10}] :foo/stuff)))
      (is (= `[(s/def :foo/b integer?)
               (s/def :foo/a integer?)
               (s/def
                 :foo/stuff
                 (s/and
                  (s/keys :opt-un [:foo/a :foo/b])
                  (s/map-of
                   (s/or :keyword keyword? :string string?)
                   integer?)))]
             (pr/infer-specs [{}
                              {"foo" 0}
                              {:a 4, "4" 4}
                              {:a 5 :b 10, "4" 10}] :foo/stuff)))
      (is (= `[(s/def :foo/b integer?)
               (s/def :foo/a integer?)
               (s/def
                 :foo/stuff
                 (s/and
                  (s/keys :opt-un [:foo/a :foo/b])
                  (s/map-of
                   (s/or :keyword keyword? :string string?)
                   integer?)))]
             (pr/infer-specs [{}
                              {:a 4, "4" 4}
                              {:a 5 :b 10, "4" 10}] :foo/stuff))))

    ;;TODO this case does not work as expected (a is not promoted to top-level as a named spec):
    ;; (pr/infer-specs [{{:a 4} 3}
    ;;               {{:a 8} 4}] ::foo)
    )

  (testing "positional (cat) specs"
    (is (= `[(s/def :foo/bar integer?)
             (s/def :foo/foo integer?)
             (s/def :foo/vector
               (s/cat
                :el0 integer?
                :el1 integer?
                :el2 (s/keys :req-un [:foo/bar :foo/foo])))]
           (pr/infer-specs [[1 2 {:foo 3 :bar 4}]]
                        :foo/vector
                        #::stats{:positional true})))

    (is (= `[(s/def :foo/bar integer?)
             (s/def :foo/foo integer?)
             (s/def
               :foo/vector
               (s/cat
                :el0 integer?
                :el1 integer?
                :el2 (s/keys :req-un [:foo/bar :foo/foo])
                :el3 (s/spec
                      (s/cat
                       :el0 double?
                       :el1 double?
                       :el2 double?))))]
           (pr/infer-specs [[1 2 {:foo 3 :bar 4} [1.2 5.4 3.1]]]
                        :foo/vector
                        #::stats{:positional true}))))

  (testing "order of or"
    (is (= `[(s/def
               :spec-provider.provider-test/stuff
               (s/or
                :map (s/and clojure.core/empty? clojure.core/map?)
                :set (s/coll-of clojure.core/any? :kind clojure.core/set?)
                :simple
                (s/or
                 :boolean boolean?
                 :double  double?
                 :integer integer?
                 :keyword keyword?)))]
           (pr/infer-specs [:k true 1.5 false #{} {} 5] ::stuff))))

  (testing "issue #1 - coll-of overrides everything"
    (is (= `[(s/def
               :spec-provider.provider-test/stuff
               (s/or
                :collection (s/coll-of integer?)
                :map (s/and clojure.core/empty? clojure.core/map?)
                :set (s/coll-of clojure.core/any? :kind clojure.core/set?)
                :simple
                (s/or
                 :boolean boolean?
                 :double  double?
                 :integer integer?
                 :keyword keyword?)))]
           (pr/infer-specs [:k true 1.5 false '(1 2 3) #{} {} 5] ::stuff))))

  (testing "nilable"
    (is (= `[(s/def ::a
               (s/nilable integer?))
             (s/def ::foo
               (s/keys :req-un [::a]))]
           (pr/infer-specs [{:a 5} {:a nil}] ::foo)))
    (is (= `[(s/def ::foo (s/nilable integer?))]
           (pr/infer-specs [1 2 nil] ::foo)))
    (is (= `[(s/def ::foo (s/coll-of (s/nilable integer?)))]
           (pr/infer-specs [[1] [2] [nil]] ::foo)))
    (is (= `[(s/def ::foo (s/nilable (s/coll-of integer?)))]
           (pr/infer-specs [[1] [2] nil] ::foo)))
    (is (= `[(s/def ::a integer?)
             (s/def
               ::foo
               (s/nilable (s/keys :req [::a])))]
           (pr/infer-specs [{::a 9} {::a 10} nil] ::foo)))
    (testing " with positional"
      (is (= `[(s/def :foo/bar integer?)
               (s/def :foo/foo integer?)
               (s/def
                 :foo/vector
                 (s/cat
                  :el0 (s/nilable integer?)
                  :el1 integer?
                  :el2 (s/nilable (s/keys :req-un [:foo/bar :foo/foo]))))]
             (pr/infer-specs [[1 2 {:foo 3 :bar 4}]
                           [nil 2 {:foo 3 :bar 4}]
                           [1 2 nil]]
                          :foo/vector
                          #::stats{:positional true})))))

  (testing "do I know you from somewhere?"
    (is (= `[(s/def :foo/zz integer?)
             (s/def :foo/b
               (s/nilable (s/keys :req-un [:foo/zz])))
             (s/def :foo/a (s/coll-of :foo/b))
             (s/def :foo/stuff (s/keys :req-un [:foo/a :foo/b]))]
           (pr/infer-specs [{:a [{:zz 1}] :b {:zz 2}}
                         {:a [{:zz 1} {:zz 4} nil] :b nil}] :foo/stuff))))

  (testing "sets"
    (is (= `[(s/def :foo/a (s/coll-of keyword? :kind clojure.core/set?))
             (s/def :foo/b (s/coll-of keyword? :kind clojure.core/set?))
             (s/def :foo/stuff (s/keys :opt-un [:foo/a :foo/b]))]
           (pr/infer-specs
            [{:b #{:a}}
             {:b #{:b}}
             {:a #{:c}}]
            :foo/stuff)))
    (is (= `[(s/def
               :foo/b (s/coll-of string? :kind clojure.core/set?))
             (s/def :foo/a (s/keys :req-un [:foo/b]))
             (s/def :foo/stuff (s/keys :opt-un [:foo/a :foo/b]))]
           (pr/infer-specs
            [{:a {:b #{"string 1" "string 2"}}}
             {:b #{"string 3"}}]
            :foo/stuff)))
    (is (= `[(s/def
               :foo/b (s/coll-of
                       (s/or :keyword keyword?
                             :string string?)
                       :kind clojure.core/set?))
             (s/def :foo/a (s/keys :req-un [:foo/b]))
             (s/def :foo/stuff (s/keys :opt-un [:foo/a :foo/b]))]
           (pr/infer-specs
            [{:a {:b #{:a "string 2"}}}
             {:b #{"string 3"}}] :foo/stuff)))

    (testing "- nilable"
      (is (= `[(s/def
                 :foo/a
                 (s/nilable
                  (s/coll-of integer? :kind clojure.core/set?)))
               (s/def :foo/stuff (s/keys :req-un [:foo/a]))]
             (pr/infer-specs [{:a #{1}} {:a nil}] :foo/stuff)))))

  (testing "rewrite/all-nilable-or"
    (is (= `[(s/def
               :foo/stuff
               (s/nilable
                (s/or
                 :integer integer?
                 :keyword keyword?
                 :string  string?)))]
           (pr/infer-specs [1 :a "string" nil] :foo/stuff))))

  (testing "numerical ranges"
    (testing "- for all keys"
      (is (= `[(s/def :spec-provider.provider-test/bar
                 (s/and integer? (clojure.core/fn [~'x] (clojure.core/<= -400 ~'x 400))))
               (s/def :spec-provider.provider-test/foo
                 (s/and integer? (clojure.core/fn [~'x] (clojure.core/<= 3 ~'x 10))))
               (s/def :spec-provider.provider-test/stuff
                 (s/keys :req-un [:spec-provider.provider-test/bar :spec-provider.provider-test/foo]))]
             (pr/infer-specs [{:foo 3, :bar -400}
                              {:foo 3, :bar 4}
                              {:foo 3, :bar 4}
                              {:foo 3, :bar 4}
                              {:foo 10, :bar 400}] ::stuff {::pr/range true}))))
    (testing "- for specific keys"
      (is (= `[(s/def :spec-provider.provider-test/bar integer?)
               (s/def :spec-provider.provider-test/foo
                 (s/and integer? (clojure.core/fn [~'x] (clojure.core/<= 3 ~'x 10))))
               (s/def :spec-provider.provider-test/stuff
                 (s/keys :req-un [:spec-provider.provider-test/bar :spec-provider.provider-test/foo]))]
             (pr/infer-specs [{:foo 3, :bar -400}
                              {:foo 3, :bar 4}
                              {:foo 3, :bar 4}
                              {:foo 3, :bar 4}
                              {:foo 10, :bar 400}] ::stuff {::pr/range #{::foo}}))))
    (testing "- for map-of"
      (is (= `[(s/def :spec-provider.provider-test/stuff
                 (s/map-of
                  (s/and integer? (clojure.core/fn [~'x] (clojure.core/<= 1 ~'x 4)))
                  (s/and integer? (clojure.core/fn [~'x] (clojure.core/<= 100 ~'x 1000)))))]
             (pr/infer-specs [{1 100 2 200}
                              {1 200 4 1000}] ::stuff {::pr/range true}))))
    (testing "- ignored when the key is not numerical"
      (is (= `[(s/def :spec-provider.provider-test/bar string?)
               (s/def :spec-provider.provider-test/foo integer?)
               (s/def :spec-provider.provider-test/stuff
                 (s/keys :req-un [:spec-provider.provider-test/bar :spec-provider.provider-test/foo]))]
             (pr/infer-specs [{:foo 3, :bar "dwdw"}
                              {:foo 3, :bar "dwdw"}
                              {:foo 3, :bar "dqdw"}
                              {:foo 3, :bar "dwdw"}
                              {:foo 10, :bar "dwdw"}] ::stuff {::pr/range #{::bar}}))))
    (testing "- with mixed values"
      (is (= `[(s/def :spec-provider.provider-test/bar
                 (s/or
                  :integer (s/and integer? (clojure.core/fn [~'x] (clojure.core/<= 1 ~'x 100)))
                  :string string?))
               (s/def :spec-provider.provider-test/foo
                 (s/and integer? (clojure.core/fn [~'x] (clojure.core/<= 3 ~'x 10))))
               (s/def :spec-provider.provider-test/stuff
                 (s/keys :req-un [:spec-provider.provider-test/bar :spec-provider.provider-test/foo]))]
             (pr/infer-specs [{:foo 3, :bar 1}
                              {:foo 3, :bar "dwdw"}
                              {:foo 3, :bar "dqdw"}
                              {:foo 3, :bar 100}
                              {:foo 10, :bar "dwdw"}] ::stuff {::pr/range true}))))))


(deftest person-spec-inference-test
  (let [persons (gen/sample (s/gen ::person/person) 100)]
    (is (= (into
            #{}
            `[(s/def :person/codes (s/coll-of keyword?))
              #?(:clj (s/def :person/bank-balance clojure.core/decimal?)
                 :cljs (s/def :person/bank-balance clojure.core/integer?))
              (s/def :person/phone-number string?)
              (s/def :person/street-number integer?)
              (s/def :person/country string?)
              (s/def :person/city string?)
              (s/def :person/street string?)
              (s/def
                :person/address
                (s/keys :req-un [:person/city :person/country :person/street] :opt-un [:person/street-number]))
              (s/def :person/age integer?)
              (s/def :person/k (s/nilable keyword?))
              (s/def :person/surname string?)
              (s/def :person/first-name string?)
              (s/def :person/id (s/or :integer integer? :string string?))
              (s/def :person/role #{:programmer :designer})
              (s/def
                :person/person
                (s/keys
                 :req
                 [:person/role]
                 :req-un
                 [:person/address :person/age :person/bank-balance :person/first-name :person/id :person/k :person/surname]
                 :opt-un
                 [:person/codes :person/phone-number]))])
           (set (pr/infer-specs persons :person/person))))))

(deftest pprint-specs-test
  (let [specs `[(s/def :person/id (s/or :numeric pos-int? :string string?))
                (s/def :person/codes (s/coll-of keyword? :max-gen 5))
                (s/def :person/first-name string?)
                (s/def :person/surname string?)
                (s/def :person/k keyword?)
                (s/def :person/bank-balance integer?)]]
    (is (= (str "(s/def ::id (s/or :numeric pos-int? :string string?))\n"
                "(s/def ::codes (s/coll-of keyword? :max-gen 5))\n"
                "(s/def ::first-name string?)\n"
                "(s/def ::surname string?)\n"
                "(s/def ::k keyword?)\n"
                "(s/def ::bank-balance integer?)\n")
           (with-out-str
             (pr/pprint-specs specs 'person 's)))))
  (testing "maps with mixed namespace keywords"
    (is (= (str/join
            "\n"
            ["(s/def :clojure.spec.gen.alpha/bar integer?)"
             "(s/def ::foo integer?)"
             "(s/def :foobar/a integer?)"
             "(s/def"
             " :foobar/my"
             " (s/keys :req [::foo :clojure.spec.gen.alpha/bar] :req-un [:foobar/a]))\n"])
           (with-out-str
             (pr/pprint-specs
              (pr/infer-specs [{:a 0 ::s/foo 1 ::gen/bar 2}
                               {:a 1 ::s/foo 10 ::gen/bar 20}
                               {:a 2 ::s/foo 100 ::gen/bar 200}] :foobar/my)
              'clojure.spec.alpha 's))))))

(deftest person-spec-inference-with-merging-test
  (let [persons (map person/add-inconsistent-id
                     (gen/sample (s/gen ::person/person) 100))]
    (is (= (into
            #{}
            `[(s/def :person/codes (s/coll-of keyword?))
              #?(:clj (s/def :person/bank-balance clojure.core/decimal?)
                 :cljs (s/def :person/bank-balance clojure.core/integer?))
              (s/def :person/phone-number string?)
              (s/def :person/id (s/or :integer integer? :keyword keyword? :string string?))
              (s/def :person/street-number integer?)
              (s/def :person/country string?)
              (s/def :person/city string?)
              (s/def :person/street string?)
              (s/def
                :person/address
                (s/keys
                 :req-un [:person/city :person/country :person/id :person/street]
                 :opt-un [:person/street-number]))
              (s/def :person/age integer?)
              (s/def :person/k (s/nilable keyword?))
              (s/def :person/surname string?)
              (s/def :person/first-name string?)
              (s/def :person/role #{:programmer :designer})
              (s/def
                :person/person
                (s/keys
                 :req [:person/role]
                 :req-un [:person/address :person/age :person/bank-balance :person/first-name :person/id :person/k :person/surname]
                 :opt-un [:person/codes :person/phone-number]))])
           (set (pr/infer-specs persons :person/person))))))
