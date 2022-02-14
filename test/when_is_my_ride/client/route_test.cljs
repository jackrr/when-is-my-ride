(ns when-is-my-ride.client.route-test
  (:require [when-is-my-ride.client.route :as sut]
            [cljs.test :refer-macros [deftest testing is]]))

(deftest icon-test
  (testing "renders abbr and color"
    (let [rendered (sut/icon {:abbr "A" :color "123456" :id "abc"})]
      (is (= (last rendered) "A"))
      (is (= (-> rendered (nth 1) :style :background-color) "#123456")))))
