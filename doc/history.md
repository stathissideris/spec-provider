# Version history

## 0.4.0

* Infer nilable: collect stats for nil values and `s/nilable` where
  appropriate.
* Fix issue #1: If a sample value was a collection, `s/coll-of` would
  override any other specs for the leaf. This is now fixed.
* Update to Clojure 1.9.0-alpha16 (spec namespaces were renamed).

## 0.3.1

Initial release
