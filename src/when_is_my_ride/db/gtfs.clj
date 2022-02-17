(ns when-is-my-ride.db.gtfs
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [manifold.stream :as s]))

(defn id-with-prefix [prefix id]
  (when (not-empty id)
    (str prefix "-" id)))

(defn feed-messages->trip-updates [feed {:keys [get-additional-fields agency query]}]
  (let [->id (partial id-with-prefix agency)]
    (for [i (range (.getEntityCount feed))
          :let [tu (-> feed (.getEntity i) .getTripUpdate)
                trip (some-> tu .getTrip)
                trip-id (-> trip .getTripId ->id)
                route-id (if (.hasRouteId trip)
                           (-> trip .getRouteId ->id)
                           (first (query '[:find [?rid]
                                           :in $ ?tid
                                           :where
                                           [?t :trip/id ?tid]
                                           [?t :route ?r]
                                           [?r :route/id ?rid]]
                                         trip-id)))]
          :when (and (some? tu) (some? trip) (< 0 (.getStopTimeUpdateCount tu)))]
      (cond-> {:agency-id agency
               :trip-id trip-id
               :route-id route-id
               :stops (map (fn [stop]
                             (let [sid (-> stop .getStopId ->id)
                                   at (-> stop .getArrival .getTime)]
                               {:stop-id sid
                                :arrival-time at
                                :ts-id (->id (str "ts-" at "-" sid))}))
                           (.getStopTimeUpdateList tu))}
        (some? get-additional-fields)
        (merge (get-additional-fields trip))))))

(defn trip-update->txn [{:keys [agency-id direction route-id trip-id stops]}]
  (conj
   (flatten (map
             (fn [{:keys [stop-id arrival-time ts-id]}]
               [(cond-> {:stop/id stop-id
                         :trip [:trip/id trip-id]}
                  (some? route-id)
                  (assoc :routes [:route/id route-id]))
                (cond-> {:trip-stop/id ts-id
                         :at arrival-time
                         :agency [:agency/id agency-id]
                         :stop [:stop/id stop-id]
                         :trip [:trip/id trip-id]}
                  (some? route-id)
                  (assoc :route [:route/id route-id]))])
             stops))
   (cond-> {:trip/id trip-id}
     (some? route-id)
     (assoc :route [:route/id route-id])
     (some? direction)
     (assoc :direction direction))
   {:route/id route-id
    :agency [:agency/id agency-id]}))

(defn- process-file [fname processor]
  (with-open [reader (-> fname io/resource io/reader)]
    (processor (csv/read-csv reader))))

(defn read-stops [txns resource-fname agency]
  (process-file
   resource-fname
   (fn [stop-d]
     (let [->id (partial id-with-prefix agency)
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
           (s/put! txns
                   [(let [parent (->id (get stop stop-parent-idx))]
                      (cond-> {:db/id "stop-id"
                               :stop/id (->id (get stop stop-id-idx))
                               :name (get stop stop-name-idx)
                               :agency [:agency/id agency]}
                        (not-empty parent)
                        (assoc :parent [:stop/id parent])))]))
         stops))))))

(defn read-routes [txns resource-fname agency]
  (process-file
   resource-fname
   (fn [data]
     (let [->id (partial id-with-prefix agency)
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
           (s/put! txns
                   [{:route/id (->id (get route id-idx))
                     :abbr (get route abbr-idx)
                     :name (get route name-idx)
                     :color (get route color-idx)
                     :agency [:agency/id agency]}]))
         routes))))))

(defn read-trips [txns resource-fname agency]
  (process-file
   resource-fname
   (fn [data]
     (let [->id (partial id-with-prefix agency)
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
           (s/put! txns
                   [{:trip/id (->id (get trip trip-id-idx))
                     :route [:route/id (->id (get trip route-id-idx))]
                     :direction (get trip direction-idx)
                     :agency [:agency/id agency]}]))
         trips))))))
