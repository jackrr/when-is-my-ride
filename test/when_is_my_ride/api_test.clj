(ns when-is-my-ride.api-test
  (:require [clojure.test :refer [deftest is testing]]
            [when-is-my-ride.api :as sut]
            [clojure.java.io :as io]
            [hato.client :as hc]
            [when-is-my-ride.db.mta :as mta]
            [when-is-my-ride.mock-db :as mock-db]))

(deftest stops
  (with-redefs
   [mta/routes ["gtfs-ace"]
    hc/get (fn [url & _]
             (cond
               (some? (re-find #"nycferry" url))
               {:body
                (-> "test/ferry-feed-sample.txt"
                    io/resource
                    mock-db/file->bytes)}
               :else
               {:body
                (-> "test/ace-feed-sample.txt"
                    io/resource
                    mock-db/file->bytes)}))]
    (testing "returns matching stops"
      (is (some #(= (:name %) "East Broadway") @(sut/stops-for "broadway"))))

    (testing "contains no duplicate parents"
      (let [res @(sut/stops-for "junc")]
        (is (= (count res) (->> res (map :id) distinct count)))))))
