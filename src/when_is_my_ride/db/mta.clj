(ns when-is-my-ride.db.mta
  (:require [datascript.core :as d]
            [when-is-my-ride.db.mta.gtfs :as gtfs]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(defn- load-stops [conn]
  (with-open [stop-reader (-> "mta/stops.txt" io/resource io/reader)
              station-reader (-> "mta/stations.csv" io/resource io/reader)]
    (let [stop-d (csv/read-csv stop-reader)
          station-d (csv/read-csv station-reader)
          stop-headers (first stop-d)
          stops (rest stop-d)
          station-headers (first station-d)
          stations (rest station-d)
          stop-id-idx (.indexOf stop-headers "stop_id")
          stop-name-idx (.indexOf stop-headers "stop_name")
          stop-parent-idx (.indexOf stop-headers "parent_station")
          station-id-idx (.indexOf station-headers "Complex ID")
          station-stop-id-idx (.indexOf station-headers "GTFS Stop ID")
          station-name-idx (.indexOf station-headers "Stop Name")]
      (doall
       (map
        (fn [stop]
          (d/transact! conn
                       [(let [parent (get stop stop-parent-idx)]
                          (cond-> {:db/id "stop-id"
                                   :stop/id (get stop stop-id-idx)
                                   :stop/name (get stop stop-name-idx)}
                            (not-empty parent)
                            (assoc :stop/parent [:stop/id parent])))]))
        stops))
      (doall
       (map
        (fn [station]
          (let [stop-id (get station station-stop-id-idx)
                station-id (get station station-id-idx)]
            (d/transact! conn
                         [{:db/ident station-id
                           :stop/id station-id
                           :stop/name (get station station-name-idx)}
                          {:db/ident stop-id
                           :stop/id stop-id
                           :stop/parent [:stop/id station-id]}])))
        stations))))
  conn)

(defn- load-trips [conn]
  (doall
   (map
    (fn [{:keys [direction route-id trip-id stops]}]
      (let [temp-route-id (str "rid" route-id)
            temp-trip-id (str "tid" trip-id)]
        (d/transact! conn (conj
                           (flatten (map (fn [{:keys [stop-id arrival-time]}]
                                           (let [temp-stop-id (str "stop-id" stop-id)]
                                             [{:stop/id stop-id
                                               :db/id temp-stop-id}
                                              {:trip-stop/stop temp-stop-id
                                               :trip-stop/at arrival-time
                                               :trip-stop/trip temp-trip-id
                                               :trip-stop/route temp-route-id}]))
                                         stops))
                           {:route/id route-id
                            :db/id temp-route-id}
                           {:trip/id trip-id
                            :db/id temp-trip-id
                            :trip/direction direction
                            :trip/route temp-route-id}))))
    (gtfs/latest-trip-data)))
  conn)

(defn load-all [conn]
  (-> conn load-stops load-trips))
