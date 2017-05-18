# Version history

## 0.4.2

* Fix issue #7 more.

## 0.4.1

* Fix issue #7: Merging of key `:spec-provider.stats/elements-coll`
  of stats of the same key was failing with an NPE.
* Fix problem where `1.0` would spec'ed as `(s/or :double double? :float float?)`.
  You now get `double?` unless it's really `(float 1.0)`.

## 0.4.0

* Infer nilable: collect stats for nil values and wrap with
  `s/nilable` where appropriate.
* Fix issue #1: If a sample value was a collection, `s/coll-of` would
  override any other specs for the leaf. This is now fixed.
* Update to Clojure 1.9.0-alpha16 (spec namespaces were renamed).

## 0.3.1

Initial release
