(ns when-is-my-ride.db.gtfs
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [manifold.stream :as s]
            [taoensso.tufte :as tufte]))

(defn id-with-prefix [prefix id]
  (when (not-empty id)
    (str prefix "-" id)))

(defn trip-update->txn [{:keys [agency-id direction destination route-id trip-id stops]}]
  (tufte/p ::trip-update->txn
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
              (assoc :direction direction)
              (some? destination)
              (assoc :destination destination))
            {:route/id route-id
             :agency [:agency/id agency-id]})))

(defn- route-id [query trip-id]
  (tufte/p ::route-id-no-cache
           (first (query '[:find [?rid]
                           :in $ ?tid
                           :where
                           [?t :trip/id ?tid]
                           [?t :route ?r]
                           [?r :route/id ?rid]]
                         trip-id))))

(defn feed-messages->txns [feed {:keys [get-additional-fields agency query]}]
  (tufte/p ::feed-messages->txns
           (map
            trip-update->txn
            (let [->id (partial id-with-prefix agency)]
              (for [i (range (.getEntityCount feed))
                    :let [tu (-> feed (.getEntity i) .getTripUpdate)
                          trip (some-> tu .getTrip)
                          trip-id (-> trip .getTripId ->id)
                          route-id (if (.hasRouteId trip)
                                     (-> trip .getRouteId ->id)
                                     (route-id query trip-id))]
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
                  (merge (get-additional-fields trip))))))))

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
           direction-idx (.indexOf headers "direction_id")
           headsign-idx (.indexOf headers "trip_headsign")]
      ;; Doall so reader can close
       (doall
        (map
         (fn [trip]
           (s/put! txns
                   [{:trip/id (->id (get trip trip-id-idx))
                     :route [:route/id (->id (get trip route-id-idx))]
                     :direction (get trip direction-idx)
                     :agency [:agency/id agency]
                     :name (get trip headsign-idx)}]))
         trips))))))
