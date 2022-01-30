(ns when-is-my-ride.api-test
  (:require [clojure.test :refer [deftest is testing]]
            [when-is-my-ride.api :as sut]
            [when-is-my-ride.mock-db :refer [with-mock-db]]))

(with-mock-db
  (deftest stops
    (testing "returns matching stops"
      (is (some #(= (:name %) "East Broadway") @(sut/stops-for "broadway"))))))
