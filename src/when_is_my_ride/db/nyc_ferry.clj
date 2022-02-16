(ns when-is-my-ride.db.nyc-ferry
  (:import [com.google.transit.realtime GtfsRealtime$FeedMessage])
  (:require [datascript.core :as d]
            [hato.client :as hc]
            [when-is-my-ride.db.gtfs :as gtfs]))

(def agency "nyc-ferry")

(defn- load-static [conn]
  (d/transact! conn [{:agency/id agency}])
  (doall (map
          (fn [datoms]
            (d/transact! conn datoms))
          (gtfs/read-stops "nyc-ferry/stops.txt" agency)))
  (doall (map
          (fn [datoms]
            (d/transact! conn datoms))
          (gtfs/read-routes "nyc-ferry/routes.txt" agency)))
  (doall (map
          (fn [datoms]
            (d/transact! conn datoms))
          (gtfs/read-trips "nyc-ferry/trips.txt" agency)))
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
            (gtfs/feed-messages->trip-updates {:agency agency :conn conn}))))
  conn)

(defn load-all [conn]
  (println "Loading NYC Ferry data")
  (let [res (-> conn load-static load-trips)]
    (println "Done loading NYC Ferry data")
    res))

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
