(ns when-is-my-ride.db.gtfs
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(defn id-with-prefix [prefix id]
  (when (not-empty id)
    (str prefix "-" id)))

(defn feed-messages->trip-updates
  ([feed {:keys [get-additional-fields namespace]}]
   (let [->id (partial id-with-prefix namespace)]
     (for [i (range (.getEntityCount feed))
           :let [tu (-> feed (.getEntity i) .getTripUpdate)
                 trip (some-> tu .getTrip)]
           :when (and (some? tu) (some? trip) (< 0 (.getStopTimeUpdateCount tu)))]
       (cond-> {:trip-id (-> trip .getTripId ->id)
                :stops (map (fn [stop]
                              {:stop-id (-> stop .getStopId ->id)
                               :arrival-time (-> stop .getArrival .getTime)})
                            (.getStopTimeUpdateList tu))}
         (.hasRouteId trip)
         (assoc :route-id (-> trip .getRouteId ->id))
         (some? get-additional-fields)
         (merge (get-additional-fields trip))))))
  ([feed] (feed-messages->trip-updates feed {})))

(defn trip-update->datoms [{:keys [direction route-id trip-id stops]}]
  (conj
   (flatten (map
             (fn [{:keys [stop-id arrival-time]}]
               [{:stop/id stop-id}
                (cond-> {:trip-stop/at arrival-time
                         :trip-stop/stop [:stop/id stop-id]
                         :trip-stop/trip [:trip/id trip-id]}
                  (some? route-id)
                  (assoc :trip-stop/route [:route/id route-id]))])
             stops))
   (cond-> {:trip/id trip-id}
     (some? route-id)
     (assoc :trip/route [:route/id route-id])
     (some? direction)
     (assoc :trip/direction direction))
   {:route/id route-id}))

(defn read-stops [resource-fname namespace]
  (with-open [stop-reader (-> resource-fname io/resource io/reader)]
    (let [->id (partial id-with-prefix namespace)
          stop-d (csv/read-csv stop-reader)
          stop-headers (first stop-d)
          stops (rest stop-d)
          stop-id-idx (.indexOf stop-headers "stop_id")
          stop-id-idx (if (= -1 stop-id-idx) 0 stop-id-idx) ; handle UTF8 signature mucking up first header
          stop-name-idx (.indexOf stop-headers "stop_name")
          stop-parent-idx (.indexOf stop-headers "parent_station")]
      ;; Doall so reader can close
      (doall
       (map
        (fn [stop]
          [(let [parent (->id (get stop stop-parent-idx))]
             (cond-> {:db/id "stop-id"
                      :stop/id (->id (get stop stop-id-idx))
                      :stop/name (get stop stop-name-idx)}
               (not-empty parent)
               (assoc :stop/parent [:stop/id parent])))])
        stops)))))

(defn read-routes [resource-fname namespace]
  (with-open [route-reader (-> resource-fname io/resource io/reader)]
    (let [->id (partial id-with-prefix namespace)
          data (csv/read-csv route-reader)
          headers (first data)
          routes (rest data)
          id-idx (.indexOf headers "route_id")
          id-idx (if (= -1 id-idx) 0 id-idx) ; handle UTF8 signature mucking up first header
          abbr-idx (.indexOf headers "route_short_name")
          name-idx (.indexOf headers "route_long_name")
          color-idx (.indexOf headers "route_color")]
      ;; Doall so reader can close
      (doall
       (map
        (fn [route]
          [{:route/id (->id (get route id-idx))
            :route/abbr (get route abbr-idx)
            :route/name (get route name-idx)
            :route/color (get route color-idx)}])
        routes)))))

(defn read-trips [resource-fname namespace]
  (with-open [route-reader (-> resource-fname io/resource io/reader)]
    (let [->id (partial id-with-prefix namespace)
          data (csv/read-csv route-reader)
          headers (first data)
          trips (rest data)
          route-id-idx (.indexOf headers "route_id")
          route-id-idx (if (= -1 route-id-idx) 0 route-id-idx) ; handle UTF8 signature mucking up first header
          trip-id-idx (.indexOf headers "trip_id")
          direction-idx (.indexOf headers "direction_id")]
      ;; Doall so reader can close
      (doall
       (map
        (fn [trip]
          [{:trip/id (->id (get trip trip-id-idx))
            :trip/route [:route/id (->id (get trip route-id-idx))]
            :trip/direction (get trip direction-idx)}])
        trips)))))
