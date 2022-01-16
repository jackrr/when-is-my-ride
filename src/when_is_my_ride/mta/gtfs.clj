(ns when-is-my-ride.mta.gtfs
  (:import [com.google.protobuf ExtensionRegistryLite]
           [com.google.transit.realtime GtfsRealtime$FeedMessage]
           [com.google.transit.realtime NyctSubway])
  (:require [hato.client :as hc]
            [when-is-my-ride.env :as env]))

(def registry
  (let [reg (ExtensionRegistryLite/newInstance)]
    (NyctSubway/registerAllExtensions reg)
    reg))

(def routes
  ["gtfs-ace" "gtfs-bdfm" "gtfs-g" "gtfs-jz" "gtfs-nqrw" "gtfs-l" "gtfs" "gtfs-si"])

(defn fetch-data [route]
  (-> (str "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2F" route)
      (hc/get {:headers {"x-api-key" (env/env "MTA_API_KEY")} :as :byte-array})
      :body))

(defn ->nyct [bytes]
  (println registry)
  (GtfsRealtime$FeedMessage/parseFrom bytes registry)

  ;(protobuf/create NyctSubway (new java.io.ByteArrayInputStream bytes))
  )

(defn route-info [route]
  (-> route fetch-data ->nyct))

(comment
  (def res (route-info "gtfs-ace"))
  (def entity (.getEntity res 0))
  (def tu (.getTripUpdate entity))
  (.getStopTimeUpdateList tu) ; stop times

  (def trip (-> "gtfs-ace" route-info (.getEntity 0) .getTripUpdate .getTrip))
  (.getRouteId trip) ; 'A'
  (def nyct-trip (.getExtension trip NyctSubway/nyctTripDescriptor))
  (.getDirection nyct-trip) ; SOUTH or NORTH


                                        ; trailing )
  )
