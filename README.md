# spec-provider

![](https://circleci.com/gh/stathissideris/spec-provider.svg?&style=shield&circle-token=8aed611e2ff989f042a00dcb5886803db7bbe34c)

This is a library that will produce a best-guess
[Clojure spec](https://clojure.org/guides/spec) based on multiple
examples of in-memory data. The inferred spec is *not* meant to be
used as is and without human supervision, it is rather a starting
point that can (and should) be refined.

The idea is analogous to F# type providers -- specifically the JSON
type provider, but the input in the case of spec-provider is any
in-memory Clojure data structure.

Since Clojure spec is still in alpha, this library should also be
considered to be in alpha -- so, highly experimental, very likely to
change, possibly flawed.

## Usage

To use this library, add this dependency to your `project.clj` file:

```
[spec-provider "0.4.11"]
```

[Version history](https://github.com/stathissideris/spec-provider/blob/master/doc/history.md)

## Use cases

The are two main use cases for spec-provider:

1. You have a lot of examples of raw data (maybe in a JSONB column of
   a PostreSQL table) and you'd like to:

   * See a summary of what shape the data is. You can use
     spec-provider as a way to explore new datasets.

   * You already know what shape your data is, and you just want some
     help getting started writing a spec for it because your data is
     deeply nested, has a lot of corner cases, you're lazy etc.

   * You *think* you know what shape your data is, but because it's
     neither typed checked nor contract checked, some exceptions have
     sneaked into it. Instead of eyeballing 100,000 maps, you run
     spec-provider on them and to your surprise you find that one of
     the fields is `(s/or :integer integer? :string string?)` instead
     of just string as you expected. You can use spec-provider as a
     data debugging tool.

2. You have an un-spec'ed function and you also have a good way to
   exercise it (via unit tests, actual usage etc). You can instrument
   the function with spec-provider, run it a few times with actual
   data, and then ask spec-provider for the function spec based on the
   data that flowed through the function.

## Inferring the spec of raw data

To infer a spec of a bunch of data just pass the data to the
`infer-specs` function:

```clojure
> (require '[spec-provider.provider :as sp])

> (def inferred-specs
    (sp/infer-specs
     [{:a 8  :b "foo" :c :k}
      {:a 10 :b "bar" :c "k"}
      {:a 1  :b "baz" :c "k"}]
     :toy/small-map))

> inferred-specs

((clojure.spec.alpha/def :toy/c (clojure.spec/or :keyword keyword? :string string?))
 (clojure.spec.alpha/def :toy/b string?)
 (clojure.spec.alpha/def :toy/a integer?)
 (clojure.spec.alpha/def :toy/small-map (clojure.spec/keys :req-un [:toy/a :toy/b :toy/c])))
```

The sequence of specs that you get out of `infer-spec` is technically
correct, but not very useful for pasting into your code. Luckily, you
can do:

```clojure
> (sp/pprint-specs inferred-specs 'toy 's)

(s/def ::c (s/or :keyword keyword? :string string?))
(s/def ::b string?)
(s/def ::a integer?)
(s/def ::small-map (s/keys :req-un [::a ::b ::c]))
```

Passing `'toy` to `pprint-specs` signals that we intend to paste this
code into the `toy` namespace, so spec names are printed using the
`::` syntax.

Passing `'s` signals that we are going to require clojure.spec as `s`,
so the calls to `clojure.spec/def` become `s/def` etc.

### Nested data structures

spec-provider will walk nested data structures in your sample data and
attempt to infer specs for everything.

Let's use clojure.spec to generate a larger sample of data with nested
structures.

```clojure
(s/def ::id (s/or :numeric pos-int? :string string?))
(s/def ::codes (s/coll-of keyword? :max-gen 5))
(s/def ::first-name string?)
(s/def ::surname string?)
(s/def ::k (nilable keyword?))
(s/def ::age (s/with-gen
               (s/and integer? pos? #(<= % 130))
               #(gen/int 130)))
(s/def :person/role #{:programmer :designer})
(s/def ::phone-number string?)

(s/def ::street string?)
(s/def ::city string?)
(s/def ::country string?)
(s/def ::street-number pos-int?)

(s/def ::address
  (s/keys :req-un [::street ::city ::country]
          :opt-un [::street-number]))

(s/def ::person
  (s/keys :req-un [::id ::first-name ::surname ::k ::age ::address]
          :opt-un [::phone-number ::codes]
          :req    [:person/role]))
```

This spec can be used to generate a reasonably large random sample of
persons:

```clojure
(def persons (gen/sample (s/gen ::person) 100))
```

Which generates structures like:

```clojure
{:id "d7FMcH52",
 :first-name "6",
 :surname "haFsA",
 :k :a-*?DZ/a,
 :age 5,
 :person/role :designer,
 :address {:street "Yrx963uDy", :city "b", :country "51w5NQ6", :street-number 53},
 :codes
 [:*.?m_o-9_j?b.N?_!a+IgUE._coE.S4l4_8_.MhN!5_!x.axztfh.x-/?*
  :*-DA?+zU-.T0u5R.evD8._r_D!*K0Q.WY-F4--.O*/**O+_Qg+
  :Bh8-A?t-f]}
```

Now watch what happens when we infer the spec of `persons`:

```clojure
> (sp/pprint-specs
   (sp/infer-specs persons :person/person)
   'person 's)

(s/def ::codes (s/coll-of keyword?))
(s/def ::phone-number string?)
(s/def ::street-number integer?)
(s/def ::country string?)
(s/def ::city string?)
(s/def ::street string?)
(s/def
 ::address
 (s/keys :req-un [::street ::city ::country] :opt-un [::street-number]))
(s/def ::age integer?)
(s/def ::k (s/nilable keyword?))
(s/def ::surname string?)
(s/def ::first-name string?)
(s/def ::id (s/or :string string? :integer integer?))
(s/def ::role #{:programmer :designer})
(s/def
 ::person
 (s/keys
  :req [::role]
  :req-un [::id ::first-name ::surname ::k ::age ::address]
  :opt-un [::phone-number ::codes]))
```

Which is very close to the original spec. We are going to break down
this result to bring attention to specific features in the following
sections.

#### Nilable

If the sample data contain any `nil` values, this is detected and
reflected in the inferred spec:

```clojure
(s/def ::k (s/nilable keyword?))
```

#### Optional detection

Things like `::street-number`, `::codes` and `::phone-number` did not
appear consistently in the sampled data, so they are correctly
identified as optional in the inferred spec.

```clojure
(s/def
 ::address
 (s/keys :req-un [::street ::city ::country] :opt-un [::street-number]))
```

#### Qualified vs unqualified keys

Most of the keys in the sample data are not qualified, and they are
detected as such in the inferred spec. The `:person/role` key is
identified as fully qualified.

```clojure
(s/def
 ::person
 (s/keys
  :req [::role]
  :req-un [::id ::first-name ::surname ::k ::age ::address]
  :opt-un [::phone-number ::codes]))
```

Note that the `s/def` for role is pretty printed as `::role` because
when calling `pprint-specs` we indicated that we are going to paste
this into the `person` namespace.

```clojure
> (sp/pprint-specs
   (sp/infer-specs persons :person/person)
   'person 's)

...

(s/def ::role #{:programmer :designer})
```

#### Enumerations

You may have also noticed that role has been identified as an
enumeration of `:programmer` and `:designer`. To see how it's decided
whether a field is an enumeration or not, we have to look under the
hood. Let's generate a small sample of roles:

```clojure
> (gen/sample (s/gen ::role) 5)

(:designer :designer :designer :designer :programmer)
```

spec-provider collects statistics about all the sample data before
deciding on the spec:

```clojure
> (require '[spec-provider.stats :as stats])
> (stats/collect-stats (gen/sample (s/gen ::role) 5) {})

#:spec-provider.stats{:distinct-values #{:programmer :designer},
                      :sample-count 5,
                      :pred-map {#function[clojure.core/keyword?] #:spec-provider.stats{:sample-count 5}}}
```

The stats include a set of distinct values observed (up to a certain
limit), the sample count for each field, and counts on each of the
predicates that the field matches -- in this case just
`keyword?`. Based on these statistics, the spec is inferred and a
decision is made on whether the value is an enumeration or not.

If the following statement is true, then the value is considered an
enumeration:

```clojure
(>= 0.1
    (/ (count distinct-values)
       sample-count))
```

In other words, if the number of distinct values found is less that
10% of the total recorded values, then the value is an
enumeration. This threshold is configurable.

Looking at the actual numbers can make this logic easier to
understand. For the small sample above:

```clojure
> (sp/infer-specs (gen/sample (s/gen ::role) 5) ::role)

((clojure.spec/def :spec-provider.person-spec/role keyword?))
```

We have 2 distinct values in a sample of 5, which is 40% of the values
being distinct. Imagine this percentage in a larger sample, say
distinct 400 values in a sample of size 2000. That doesn't sound
likely to be an enumeration, so it's interpreted as a normal value.

If you increase the sample:

```clojure
> (sp/infer-specs (gen/sample (s/gen ::role) 100) ::role)

((clojure.spec/def :spec-provider.person-spec/role #{:programmer :designer}))
```

We have 2 distinct values in a sample of 100, which is 2%, which means
that the same values appear again and again in the sample, so it must
be an enumeration.

#### Merging

clojure-spec makes the same assumption as clojure.spec that keys that
have same name also have the same data shape as their value, even when
they appear in different maps. This means that the specs from
different maps are merged by key.

To demonstrate this we need to "spike" the generated persons with an
id field that's inconsistent with the existing
`(s/or :numeric pos-int? :string string?)`:

```clojure
(defn add-inconsistent-id [person]
  (if (:address person)
    (assoc-in person [:address :id] (gen/generate (gen/keyword)))
    person))

(def persons-spiked (map add-inconsistent-id (gen/sample (s/gen ::person) 100)))
```

Inferring the spec of `persons-spiked` yields a different result for
ids:

```clojure
> (sp/pprint-specs
   (sp/infer-specs persons-spiked :person/person)
   'person 's)

...
(s/def ::id (s/or :string string? :integer integer? :keyword keyword?))
...
```

#### Do I know you from somewhere?

This feature is not illustrated by the person example, but before
returning them, spec-provider will walk the inferred specs and look
for forms that already occur elsewhere and replace them with the name
of the known spec. For example:

```clojure
> (sp/pprint-specs
    (sp/infer-specs [{:a [{:zz 1}] :b {:zz 2}}
                     {:a [{:zz 1} {:zz 4} nil] :b nil}] ::foo) *ns* 's)

(s/def ::zz integer?)
(s/def ::b (s/nilable (s/keys :req-un [::zz])))
(s/def ::a (s/coll-of ::b))
(s/def ::foo (s/keys :req-un [::a ::b]))
```

In this case, because maps like `{:zz 2}` appear under the key `:b`,
spec-provider knows what to call them, so it uses that name for
`(s/def ::a (s/coll-of ::b))`. This replacement is not performed if
the spec definition is a predicate from the `clojure.core` namespace.

#### Inferring specs with numerical ranges

spec-provider collects stats about the min/max values of numerical
fields, but will not output them in the inferred spec by default. To
get range predicates in your specs you have to pass the
`:spec-provider.provider/range` option:

```clojure
> (require '[spec-provider.provider :refer :all :as sp])

> (pprint-specs
    (infer-specs [{:foo 3, :bar -400}
                  {:foo 3, :bar 4}
                  {:foo 10, :bar 400}] ::stuff {::sp/range true})
    *ns* 's)

(s/def ::bar (s/and integer? (fn [x] (<= -400 x 400))))
(s/def ::foo (s/and integer? (fn [x] (<= 3 x 10))))
(s/def ::stuff (s/keys :req-un [::bar ::foo]))
```

You can also restrict range predicates to specific keys by passing a
set of qualified keys that are the names of the specs that should get
a range predicate:

```clojure
> (sp/pprint-specs
    (sp/infer-specs [{:foo 3, :bar -400}
                     {:foo 3, :bar 4}
                     {:foo 10, :bar 400}] ::stuff {::sp/range #{::foo}})
    *ns* 's)

(s/def ::bar integer?)
(s/def ::foo (s/and integer? (fn [x] (<= 3 x 10))))
(s/def ::stuff (s/keys :req-un [::bar ::foo]))
```

### How it's done

Inferring a spec from raw data is a two step process: Stats collection
and then summarization of the stats into specs.

First each data structure is visited recursively and statistics are
collected at each level about the types of values that appear, the
distinct values for each field (up to a limit), min and max values for
numbers, lengths for sequences etc.

Two important points about stats collection:

* Spec-provider **will not** run out of memory even if you throw a lot
  of data at it because it updates the same statistics data structure
  with every new example datum it receives.

* Collecting stats will (at least partly) realize lazy sequences.

After stats collection, code from the `spec-provider.provider`
namespace goes through the stats and it summarizes it as a collection
of specs.

### Options

Undocumented: there is a number of options that can affect how the
sample stats are collected.

## Inferring the spec of functions

Undocumented/under development: there is experimental support for
instrumenting functions for the purpose of inferring the spec of args
and return values.

## Limitations

* There is no attempt to infer the regular expression of collections.
* There is no attempt to infer tuples.
* There is no attempt to infer `multi-spec`.
* For functions, only the `:args` and `:ret` parts of the spec is
  generated, the `:fn` part is up to you.
* Spec-provider assumes that you want to follow the Clojure spec
  convention that the same map keys identify the same "entity", so it
  will merge stats that appear under the identical keys but in
  different parts of your tree structure. This may not be what you
  want. For more details see the "Merging" section.

## FAQ

* Will I run out of memory if I pass a lot of examples of my data to
  `infer-specs`?

  No, stats collection works by updating the same data structure with
  every example of data received. The data structure will initially
  grow a bit and then maintain a constant size. That means that you
  can use a lazy sequence to stream your huge table through it if you
  feel that's necessary (not tested!).

* Can I do this for Prismatic schema?

  The hard part of inferring a spec is collecting the
  statistics. Summarizing the stats as specs was relatively easy, so
  plugging in a different "summarizer" that will output schemas from
  the same stats should be possible. Look at the `provider` namespace,
  write the schema equivalent and send me a pull request!

## Contributors

* [Stathis Sideris](https://github.com/stathissideris) - original author
* [Paulo Rafael Feodrippe](https://github.com/pfeodrippe)

## License

Copyright © 2016-2017 Stathis Sideris

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
