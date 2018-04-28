(ns spec-provider.cljs-test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [spec-provider.merge-test]
            [spec-provider.provider-test]
            [spec-provider.stats-test]))

(doo-tests 'spec-provider.merge-test
           'spec-provider.provider-test
           'spec-provider.stats-test)
