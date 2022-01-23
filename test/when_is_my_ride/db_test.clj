(ns when-is-my-ride.db-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest testing is]]
            [hato.client :as hc]
            [when-is-my-ride.db :as sut]
            [when-is-my-ride.db.mta :as mta]))

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(deftest db-integration
  (with-redefs [mta/routes ["gtfs-ace"]
                hc/get (fn [url & _]
                         (cond
                           (some? (re-find #"nycferry" url))
                           {:body
                            (-> "test/ferry-feed-sample.txt"
                                io/resource
                                file->bytes)}
                           :else
                           {:body
                            (-> "test/ace-feed-sample.txt"
                                io/resource
                                file->bytes)}))]
    (testing "contains queryable routes"
      (is (every?
           (fn [route]
             (not-empty (sut/q '[:find ?r
                                 :in $ ?name
                                 :where
                                 [?r :abbr ?name]]
                               route)))
           ["A" "C" "E"]) "Contains routes for A,C,E trains"))

    (testing "contains subway stop names"
      (let [stop (sut/q '[:find ?name :where
                          [?a :agency/id "mta"]
                          [?s :agency ?a]
                          [?s :stop/id ?sid]
                          [(re-find #"F18N" ?sid)]
                          [?s :name ?name]])]
        (is (= (-> stop first first) "York St"))))

    (testing "contains ferry stop names"
      (let [query-res (sut/q '[:find ?name :where
                               [?a :agency/id "nyc-ferry"]
                               [?s :agency ?a]
                               [?s :stop/id ?sid]
                               [(re-find #"\-20$" ?sid)]
                               [?s :name ?name]])]
        (is (= (-> query-res first first) "Dumbo/Fulton Ferry"))))

    (testing "contains trip-stops with relationships"
      (is (not-empty
           (sut/q '[:find ?stop ?at ?trip
                    :where
                    [?r :abbr "A"]
                    [?ts :route ?r]
                    [?ts :stop ?s]
                    [?s :stop/id ?stop]
                    [?ts :at ?at]
                    [?ts :trip ?t]
                    [?t :trip/id ?trip]]))
          "Has at least one stop"))

    (testing "contains ferry trip stops"
      (is (not-empty
           (sut/q '[:find ?stop ?at ?trip
                    :where
                    [?r :abbr "ER"]
                    [?t :route ?r]
                    [?ts :trip ?t]
                    [?ts :stop ?s]
                    [?s :stop/id ?stop]
                    [?ts :at ?at]
                    [?t :trip/id ?trip]]))
          "Has at least one stop"))))
