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
                                 [?r :route/abbr ?name]]
                               route)))
           ["A" "C" "E"]) "Contains routes for A,C,E trains"))

    (testing "contains subway stop names"
      (let [stop (sut/q '[:find ?name :where [?s :stop/id "mta-F18N"] [?s :stop/name ?name]])]
        (is (= (-> stop first first) "York St"))))

    (testing "contains ferry stop names"
      (let [query-res (sut/q '[:find ?name :where [?s :stop/id "nyc-ferry-20"] [?s :stop/name ?name]])]
        (is (= (-> query-res first first) "Dumbo/Fulton Ferry"))))

    (testing "contains trip-stops with relationships"
      (is (not-empty
           (sut/q '[:find ?stop ?at ?trip
                    :where
                    [?r :route/abbr "A"]
                    [?ts :trip-stop/route ?r]
                    [?ts :trip-stop/stop ?s]
                    [?s :stop/id ?stop]
                    [?ts :trip-stop/at ?at]
                    [?ts :trip-stop/trip ?t]
                    [?t :trip/id ?trip]]))
          "Has at least one stop"))

    (testing "contains ferry trip stops"
      (is (not-empty
           (sut/q '[:find ?stop ?at ?trip
                    :where
                    [?r :route/abbr "ER"]
                    [?t :trip/route ?r]
                    [?ts :trip-stop/trip ?t]
                    [?ts :trip-stop/stop ?s]
                    [?s :stop/id ?stop]
                    [?ts :trip-stop/at ?at]
                    [?t :trip/id ?trip]]))
          "Has at least one stop"))))
