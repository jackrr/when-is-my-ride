(ns when-is-my-ride.db.mta.gtfs
  (:import [com.google.protobuf ExtensionRegistryLite]
           [com.google.transit.realtime GtfsRealtime$FeedMessage]
           [com.google.transit.realtime NyctSubway])
  (:require [hato.client :as hc]
            [when-is-my-ride.env :as env]
            [when-is-my-ride.db.gtfs :as gtfs]))

(def ^:private registry
  (let [reg (ExtensionRegistryLite/newInstance)]
    (NyctSubway/registerAllExtensions reg)
    reg))

(def ^:private routes
  ["gtfs-ace" "gtfs-bdfm" "gtfs-g" "gtfs-jz" "gtfs-nqrw" "gtfs-l" "gtfs" "gtfs-si"])

(defn- fetch-data [route]
  (-> (str "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2F" route)
      (hc/get {:headers {"x-api-key" (env/env "MTA_API_KEY")} :as :byte-array})
      :body
      (GtfsRealtime$FeedMessage/parseFrom registry)))

(defn- trip-data [route]
  (-> route
      fetch-data
      (gtfs/feed-messages->trip-updates
       {:get-additional-fields
        (fn [trip]
          {:direction (-> trip
                          (.getExtension NyctSubway/nyctTripDescriptor)
                          .getDirection
                          .toString)})})))

(defn latest-trip-data []
  (flatten (map trip-data routes)))

(comment
  (def res (fetch-data "gtfs-ace"))
  (def entity (.getEntity res 0))
  (def tu (.getTripUpdate entity))
  (.getStopTimeUpdateList tu) ; stop times

  (def trip (-> "gtfs-ace" fetch-data (.getEntity 0) .getTripUpdate .getTrip))
  (.getRouteId trip) ; 'A'
  (def nyct-trip (.getExtension trip NyctSubway/nyctTripDescriptor))
  (.getDirection nyct-trip) ; SOUTH or NORTH
  ; ensure trailing )
  )
