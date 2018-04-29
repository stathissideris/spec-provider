(ns spec-provider.rewrite-test
  (:require [spec-provider.rewrite :as sut]
            [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as s]))

(deftest merge-same-name-defs-test
  (is
   (= `((s/def
          :spec-provider.rewrite-test/a
          (s/or :case-0 string?
                :case-1 int?))
        (s/def :spec-provider.rewrite-test/b string?)
        (s/fdef foo :fn (s/cat :a string? :b int?)))
      (sut/merge-same-name-defs
       [`(s/def ::a string?)
        `(s/def ::b string?)
        `(s/def ::a int?)
        `(s/fdef foo :fn (s/cat :a string? :b int?))]))))

(deftest fix-or-names-test
  (is
   (= `(s/or :string string?
             :integer integer?)
      (@#'sut/fix-or-names
       `(s/or :case-0 string?
              :case-1 integer?)))))

(deftest flatten-ors-test
  (is
   (= `(s/or
        :string string?
        :integer integer?
        :integer integer?)
      (sut/flatten-ors
       `(s/or :case-0  (s/or :string  string?
                             :integer integer?)
              :integer integer?)))))

(deftest distinct-or*-test
  (sut/distinct-or*
   `(s/or
     :string string?
     :integer integer?
     :b integer?)))
