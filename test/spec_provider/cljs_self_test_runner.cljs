(ns spec-provider.cljs-self-test-runner
  (:require [clojure.test :refer [run-tests]]
            [spec-provider.merge-test]
            [spec-provider.provider-test]
            [spec-provider.stats-test]))

(run-tests ;;'spec-provider.rewrite-test
           'spec-provider.merge-test
           'spec-provider.stats-test
           'spec-provider.provider-test)
