(ns when-is-my-ride.db.gtfs)

(defn feed-messages->trip-updates
  ([feed {:keys [get-additional-fields]}]
   (for [i (range (.getEntityCount feed))
         :let [tu (-> feed (.getEntity i) .getTripUpdate)
               trip (some-> tu .getTrip)]
         :when (and (some? tu) (some? trip) (< 0 (.getStopTimeUpdateCount tu)))]
     (cond-> {:route-id (.getRouteId trip)
              :trip-id (.getTripId trip)
              :stops (map (fn [stop]
                            {:stop-id (.getStopId stop)
                             :arrival-time (-> stop .getArrival .getTime)})
                          (.getStopTimeUpdateList tu))}
       (some? get-additional-fields)
       (merge (get-additional-fields trip)))))
  ([feed] (feed-messages->trip-updates feed {})))
