# spec-provider

This is a library that will produce a best-guess
[Clojure spec](https://clojure.org/guides/spec) based on multiple
examples of in memory data. The inferred spec is *not* meant to be
used as is and without human supervision, it is rather a starting
point that can (and should) be refined.

The idea is analogous to F# type providers -- specifically the JSON
type provider, but the input in the case of spec-provider is any
in-memory Clojure data structure.

Since Clojure spec is still in alpha, this library should also be
considered to be in alpha, so highly experimental, very likely to
change, possibly flawed.

## Usage

To use this library, add this dependency to your `project.clj` file:

???

## Use cases

The are two main use cases for spec-provider:

1. You have a lot of examples of raw data (maybe in a JSONB column of
   a PostreSQL table) and you'd like to:

   * See a summary of what shape the data is. You can use
     spec-provider as a way to explore new datasets.

   * You already know what shape your data is, you just want some
     helping getting started writing a spec because your data is
     deeply nested, has a lot of corner cases, you're lazy etc.

   * You *think* you know what shape your data is, but because it's
     neither typed checked nor contract checked, some exceptions have
     sneaked into it. Instead of eyeballing 100,000 maps, you run
     spec-provider on them and to your surprise you find that one of
     the fields is `(s/or :integer integer? :string string?)` instead
     of just string as you expected. You can use spec-provider as a
     data debugging tool.

2. You have an un-speced function and you also have a good way to
   exercise it (via unit tests, actual usage etc). You can instrument
   the function with spec-provider, run it a few times with actual
   data, and then ask spec-provider for the function spec based on the
   data that flowed through the function.

### Inferring the spec of raw data

To infer a spec of a bunch of data just pass the data to the
`infer-spec` function:

```clojure
> (require '[spec-provider.provider :as sp])

> (def inferred-specs
    (sp/infer-spec
     [{:a 8 :b "foo" :c :k}
      {:a 10 :b "bar" :c "k"}]
     :small-map))

> inferred-specs

((clojure.spec/def :c (clojure.spec/or :keyword keyword? :string string?))
 (clojure.spec/def :b string?)
 (clojure.spec/def :a integer?)
 (clojure.spec/def :small-map (clojure.spec/keys :req-un [:a :b :c])))
```

The sequence of specs that you get out of `infer-spec` is technically
correct, but not very useful for pasting into your code. Luckily, you
can do:

```clojure
> (sp/pprint-specs inferred-specs 'foo 's)

(s/def :c (s/or :keyword keyword? :string string?))
(s/def :b string?)
(s/def :a integer?)
(s/def :small-map (s/keys :req-un [:a :b :c]))
```

??? explain 'foo and 's

### Inferring the spec of functions

???

## License

Copyright © 2016 Stathis Sideris

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
