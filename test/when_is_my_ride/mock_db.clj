(ns when-is-my-ride.mock-db
  (:require [clojure.java.io :as io]
            [hato.client :as hc]
            [when-is-my-ride.db.mta :as mta]))

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defmacro with-mock-db [& stuff-to-do]
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
    stuff-to-do))
