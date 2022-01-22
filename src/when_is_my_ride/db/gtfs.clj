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

(defn trip-update->datoms [{:keys [direction route-id trip-id stops]}]
  (conj
   (flatten (map
             (fn [{:keys [stop-id arrival-time]}]
               [{:stop/id stop-id}
                {:trip-stop/at arrival-time
                 :trip-stop/stop [:stop/id stop-id]
                 :trip-stop/route [:route/id route-id]
                 :trip-stop/trip [:trip/id trip-id]}])
             stops))
   (cond-> {:trip/id trip-id
            :trip/route [:route/id route-id]}
     (some? direction)
     (assoc :trip/direction direction))
   {:route/id route-id}))
