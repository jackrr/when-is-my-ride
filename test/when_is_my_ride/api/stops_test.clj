(ns when-is-my-ride.api.stops-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [hato.client :as hc]
            [when-is-my-ride.api.stops :as sut]
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
      (is (some #(= (:name %) "East Broadway") (sut/stops-for "broadway"))))

    (testing "contains no duplicate parents"
      (let [res (sut/stops-for "junc")]
        (is (= (count res) (->> res (map :id) distinct count)))))

    (testing "contains route info"
      (is (every? (fn [stop]
                    (let [routes (:routes stop)]
                      (and (< 0 (count routes))
                           (every? (fn [route]
                                     (and (some? (:abbr route))
                                          (some? (:color route))
                                          (some? (:agency route))))
                                   routes))))
                  (sut/stops-for "Jackson Hts-Roosevelt Av / 74 St-Broadway"))))))
