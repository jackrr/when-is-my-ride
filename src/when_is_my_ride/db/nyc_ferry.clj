(ns when-is-my-ride.db.nyc-ferry
  (:import [com.google.transit.realtime GtfsRealtime$FeedMessage])
  (:require [manifold.stream :as s]
            [hato.client :as hc]
            [when-is-my-ride.db.gtfs :as gtfs]))

(def agency "nyc-ferry")

(defn- load-static [txns]
  (s/put! txns [{:agency/id agency}])
  (gtfs/read-stops txns "nyc-ferry/stops.txt" agency)
  (gtfs/read-routes txns "nyc-ferry/routes.txt" agency)
  (gtfs/read-trips txns "nyc-ferry/trips.txt" agency))

(defn- load-trips [txns query]
  (doall
   (map #(->> % gtfs/trip-update->txn (s/put! txns))
        (-> (hc/get
             "http://nycferry.connexionz.net/rtt/public/utility/gtfsrealtime.aspx/tripupdate"
             {:as :byte-array})
            :body
            GtfsRealtime$FeedMessage/parseFrom
            (gtfs/feed-messages->trip-updates {:agency agency :query query})))))

(defn load-all [txns query]
  (println "Loading NYC Ferry data")
  (load-static txns)
  (load-trips txns query)
  (println "Done loading NYC Ferry data"))

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
