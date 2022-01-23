(ns when-is-my-ride.db.mta
  (:import [com.google.protobuf ExtensionRegistryLite]
           [com.google.transit.realtime GtfsRealtime$FeedMessage]
           [com.google.transit.realtime NyctSubway])
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [datascript.core :as d]
            [hato.client :as hc]
            [when-is-my-ride.env :as env]
            [when-is-my-ride.db.gtfs :as gtfs]))

(def agency "mta")

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
  (map gtfs/trip-update->datoms
       (-> route
           fetch-data
           (gtfs/feed-messages->trip-updates
            {:agency agency
             :get-additional-fields
             (fn [trip]
               {:direction (-> trip
                               (.getExtension NyctSubway/nyctTripDescriptor)
                               .getDirection
                               .toString)})}))))

(defn- load-static [conn]
  (d/transact! conn [{:agency/id agency}])
  (doall (map
          (fn [datoms]
            (d/transact! conn datoms))
          (gtfs/read-stops "mta/stops.txt" agency)))
  (doall (map
          (fn [datoms]
            (d/transact! conn datoms))
          (gtfs/read-routes "mta/routes.txt" agency)))
  (with-open [station-reader (-> "mta/stations.csv" io/resource io/reader)]
    (let [->id (partial gtfs/id-with-prefix agency)
          station-d (csv/read-csv station-reader)
          station-headers (first station-d)
          stations (rest station-d)
          station-id-idx (.indexOf station-headers "Complex ID")
          station-stop-id-idx (.indexOf station-headers "GTFS Stop ID")
          station-name-idx (.indexOf station-headers "Stop Name")]
      (doall
       (map
        (fn [station]
          (let [stop-id (->id (get station station-stop-id-idx))
                station-id (->id (get station station-id-idx))]
            (d/transact! conn
                         [{:db/ident station-id
                           :stop/id station-id
                           :name (get station station-name-idx)
                           :agency [:agency/id agency]}
                          {:db/ident stop-id
                           :stop/id stop-id
                           :agency [:agency/id agency]
                           :parent [:stop/id station-id]}])))
        stations))))
  conn)

(defn- load-trips [conn]
  (doall (map (fn [datoms]
                (d/transact! conn datoms))
              (apply concat (map trip-data routes))))
  conn)

(defn load-all [conn]
  (-> conn load-static load-trips))

(comment
  (def res (fetch-data "gtfs-ace"))
  (def entity (.getEntity res 0))
  (def tu (.getTripUpdate entity))
  (.getStopTimeUpdateList tu) ; stop times

  (def trip (-> "gtfs-ace" fetch-data (.getEntity 0) .getTripUpdate .getTrip))
  (.getRouteId trip) ; 'A'
  (def nyct-trip (.getExtension trip NyctSubway/nyctTripDescriptor))
  (.getDirection nyct-trip) ; SOUTH or NORTH
                            ;
  (with-open
   [out (clojure.java.io/output-stream "ace-feed-sample.txt")]
    (.write out (:body (hc/get "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-ace"
                               {:headers {"x-api-key" (env/env "MTA_API_KEY")} :as :byte-array}))))
  ;
  )
