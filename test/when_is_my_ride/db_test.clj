(ns when-is-my-ride.db-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest testing is]]
            [hato.client :as hc]
            [when-is-my-ride.db :as sut]
            [when-is-my-ride.db.mta.gtfs :as mta.gtfs]))

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(deftest db-integration
  (with-redefs [mta.gtfs/routes ["gtfs-ace"]
                hc/get (fn [& _]
                         {:body
                          (-> "test/ace-feed-sample.txt"
                              io/resource
                              file->bytes)})]
    (testing "contains queryable routes"
      (is (every?
           (fn [route]
             (not-empty (sut/q '[:find ?r
                                 :in $ ?name
                                 :where
                                 [?r :route/id ?name]]
                               route)))
           ["A" "C" "E"]) "Contains routes for A,C,E trains"))

    (testing "contains trip-stops with relationships"
      (is (not-empty
           (sut/q '[:find ?stop ?at ?trip
                    :where
                    [?r :route/id "A"]
                    [?ts :trip-stop/route ?r]
                    [?ts :trip-stop/stop ?s]
                    [?s :stop/id ?stop]
                    [?ts :trip-stop/at ?at]
                    [?ts :trip-stop/trip ?t]
                    [?t :trip/id ?trip]]))
          "Has at least one stop"))))
