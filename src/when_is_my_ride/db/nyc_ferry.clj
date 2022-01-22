(ns when-is-my-ride.db.nyc-ferry
  (:import [com.google.transit.realtime GtfsRealtime$FeedMessage])
  (:require [datascript.core :as d]
            [hato.client :as hc]
            [when-is-my-ride.db.gtfs :as gtfs]))

(def id-namespace "nyc-ferry")

(defn- load-static [conn]
  (doall (map
          (fn [datoms]
            (d/transact! conn datoms))
          (gtfs/read-stops "nyc-ferry/stops.txt" id-namespace)))
  (doall (map
          (fn [datoms]
            (d/transact! conn datoms))
          (gtfs/read-routes "nyc-ferry/routes.txt" id-namespace)))
  (doall (map
          (fn [datoms]
            (d/transact! conn datoms))
          (gtfs/read-trips "nyc-ferry/trips.txt" id-namespace)))
  conn)

(defn- load-trips [conn]
  (doall
   (map (fn [trip-update]
          (d/transact! conn (gtfs/trip-update->datoms trip-update)))
        (-> (hc/get
             "http://nycferry.connexionz.net/rtt/public/utility/gtfsrealtime.aspx/tripupdate"
             {:as :byte-array})
            :body
            GtfsRealtime$FeedMessage/parseFrom
            (gtfs/feed-messages->trip-updates {:namespace id-namespace}))))
  conn)

(defn load-all [conn]
  (-> conn load-static load-trips))

(comment
  (-> (hc/get
       "http://nycferry.connexionz.net/rtt/public/utility/gtfsrealtime.aspx/tripupdate"
       {:as :byte-array})
      :body
      GtfsRealtime$FeedMessage/parseFrom)
  (with-open
   [out (clojure.java.io/output-stream "ferry-feed-sample.txt")]
    (.write out (:body (hc/get "http://nycferry.connexionz.net/rtt/public/utility/gtfsrealtime.aspx/tripupdate"
                               {:as :byte-array}))))
  ;
  )
