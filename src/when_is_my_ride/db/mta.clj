(ns when-is-my-ride.db.mta
  (:import [com.google.protobuf ExtensionRegistryLite]
           [com.google.transit.realtime GtfsRealtime$FeedMessage]
           [com.google.transit.realtime NyctSubway])
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :refer [debug]]
            [hato.client :as hc]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [when-is-my-ride.env :as env]
            [when-is-my-ride.db.gtfs :as gtfs]
            [taoensso.tufte :as tufte]
            [datascript.core :as ds]))

(def agency "mta")

(def ^:private registry
  (let [reg (ExtensionRegistryLite/newInstance)]
    (NyctSubway/registerAllExtensions reg)
    reg))

(def routes
  ["gtfs-ace" "gtfs-bdfm" "gtfs-g" "gtfs-jz" "gtfs-nqrw" "gtfs-l" "gtfs" "gtfs-si"])

(defn- fetch-data [route]
  (tufte/p ::fetch-data
           (-> (str "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2F" route)
               (hc/get {:headers {"x-api-key" (or (env/env "MTA_API_KEY") "")} :as :byte-array})
               :body
               (GtfsRealtime$FeedMessage/parseFrom registry))))

(defn- dest-from-train-id [train-id]
  (let [match (re-find #".*\/(\w{3})$" train-id)]
    (when-not match (println "No match for " train-id)))
  (->> train-id
       (re-find #".*\/(\w{3})$")
       last))

(defn- trip-txns [query route]
  (tufte/p ::trip-txns
           (-> route
               fetch-data
               (gtfs/feed-messages->txns
                {:agency agency
                 :query query
                 :get-additional-fields
                 (fn [trip]
                   (let [ext (.getExtension trip NyctSubway/nyctTripDescriptor)]
                     {:direction (-> ext .getDirection .toString)
                      :destination (some-> ext
                                           .getTrainId
                                           dest-from-train-id)}))}))))

(defn- load-trip [txns query route]
  (tufte/p ::load-trip
           (doall
            (map (fn [tx]
                   (s/put! txns tx))
                 (trip-txns query route)))))

(defn load-realtime [txns query]
  (debug "Loading realtime MTA data")
  @(apply d/zip
          (map #(d/future (load-trip txns query %)) routes))
  (debug "Done loading realtime MTA data")
  (s/close! txns))

(defn load-static [txns _]
  (debug "Loading static MTA data")
  (tufte/p ::load-static
           (s/put! txns [{:agency/id agency}])
           (gtfs/read-stops txns "mta/stops.txt" agency)
           (gtfs/read-routes txns "mta/routes.txt" agency)
           (with-open [station-reader (-> "mta/stations.csv" io/resource io/reader)]
             (let [->id (partial gtfs/id-with-prefix agency)
                   station-d (csv/read-csv station-reader)
                   station-headers (first station-d)
                   stations (rest station-d)
                   station-id-idx (.indexOf station-headers "Complex ID")
                   station-stop-id-idx (.indexOf station-headers "GTFS Stop ID")
                   station-name-idx (.indexOf station-headers "Stop Name")
                   structured (map (fn [s] {:id (->id (get s station-stop-id-idx))
                                            :complex-id (->id (str "cplx-" (get s station-id-idx)))
                                            :name (get s station-name-idx)})
                                   stations)
                   complexes  (->> structured
                                   (reduce (fn [cs {:keys [complex-id name]}]
                                             (if (get cs complex-id)
                                               (update cs complex-id
                                                       (fn [cplx]
                                                         (let [names (distinct (conj (:names cplx) name))]
                                                           {:n (inc (:n cplx))
                                                            :names names
                                                            :name (str/join " / " names)})))
                                               (assoc cs complex-id {:n 1
                                                                     :names [name]})))
                                           {}))]
               (doall
                (map
                 (fn [{:keys [id complex-id]}]
                   (when (< 1 (get-in complexes [complex-id :n]))
                     (s/put! txns
                             [{:db/ident complex-id
                               :stop/id complex-id
                               :name (get-in complexes [complex-id :name])
                               :agency [:agency/id agency]}
                              {:db/ident id
                               :stop/id id
                               :agency [:agency/id agency]
                               :parent [:stop/id complex-id]}])))
                 structured))))
           (s/close! txns))
  (debug "Done loading static MTA data"))

(comment

  (def res (fetch-data "gtfs-ace"))
  (def entity (.getEntity res 0))
  (def tu (.getTripUpdate entity))
  (.getStopTimeUpdateList tu) ; stop times

  (def trip (-> "gtfs-ace" fetch-data (.getEntity 0) .getTripUpdate .getTrip))
  (.getRouteId trip) ; 'A'
  (def nyct-trip (.getExtension trip NyctSubway/nyctTripDescriptor))
  (.getDirection nyct-trip) ; SOUTH or NORTH

  (do
    (require 'datascript.core)
    (require 'when-is-my-ride.db.schema)
    (let [db (ds/create-conn when-is-my-ride.db.schema/schema)
          query (fn [& args] (apply (partial datascript.core/q (first args) @db) (rest args)))]
      (first (trip-txns query "gtfs-ace"))))

  (dest-from-train-id "1A 1005 FAR/207")
  (with-open
   [out (clojure.java.io/output-stream "ace-feed-sample.txt")]
    (.write out (:body (hc/get "https://api-endpoint.mta.info/Dataservice/mtagtfsfeeds/nyct%2Fgtfs-ace"
                               {:headers {"x-api-key" (env/env "MTA_API_KEY")} :as :byte-array}))))
  ;
  )
