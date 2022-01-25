(ns when-is-my-ride.client.core-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [when-is-my-ride.client.core :as core]))

(deftest fake-test
  (testing "fake description"
    (is (= 1 1))))
