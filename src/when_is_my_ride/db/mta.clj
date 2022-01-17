(ns when-is-my-ride.db.mta
  (:require [datascript.core :as d]
            [when-is-my-ride.db.mta.gtfs :as gtfs]))

;; All stops, grouped under common stations via parent_station
;; Also grouped under common "complex id" from stations.csv
(defn- load-stops [conn]
  ; TODO
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
