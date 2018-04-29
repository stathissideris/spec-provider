(ns spec-provider.cljs-test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [pjstadig.humane-test-output :as hto]
            [spec-provider.merge-test]
            [spec-provider.provider-test]
            [spec-provider.stats-test]))

(doo-tests ;;'spec-provider.rewrite-test
           'spec-provider.merge-test
           'spec-provider.stats-test
           'spec-provider.provider-test)
