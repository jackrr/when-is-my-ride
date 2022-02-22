(ns when-is-my-ride.db.nyc-ferry
  (:import [com.google.transit.realtime GtfsRealtime$FeedMessage])
  (:require [clojure.tools.logging :refer [debug]]
            [manifold.stream :as s]
            [hato.client :as hc]
            [when-is-my-ride.db.gtfs :as gtfs]
            [taoensso.tufte :as tufte]))

(def agency "nyc-ferry")

(defn load-static [txns _]
  (debug "Loading static NYC ferry data")
  (s/put! txns [{:agency/id agency}])
  (gtfs/read-stops txns "nyc-ferry/stops.txt" agency)
  (gtfs/read-routes txns "nyc-ferry/routes.txt" agency)
  (gtfs/read-trips txns "nyc-ferry/trips.txt" agency)
  (s/close! txns)
  (debug "Done loading static NYC ferry data"))

(defn load-realtime [txns query]
  (debug "Loading realtime NYC Ferry data")
  (tufte/p ::load-trips
           (doall
            (map #(s/put! txns %)
                 (-> (hc/get
                      "http://nycferry.connexionz.net/rtt/public/utility/gtfsrealtime.aspx/tripupdate"
                      {:as :byte-array})
                     :body
                     GtfsRealtime$FeedMessage/parseFrom
                     (gtfs/feed-messages->txns {:agency agency :query query}))))
           (s/close! txns))
  (debug "Done loading realtime NYC ferry data"))

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
