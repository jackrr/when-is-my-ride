(ns when-is-my-ride.db.mta.trip
  (:require [when-is-my-ride.db.gtfs :as gtfs]))

(def ^:private TRIP_CACHE (atom nil))
(defn- load-trip-data! []
  (gtfs/process-file
   "mta/trips.txt"
   (fn [rows]
     (let [headers (first rows)
           trips (rest rows)
           route-id-idx (.indexOf headers "route_id")
           trip-id-idx (.indexOf headers "trip_id")
           name-idx (.indexOf headers "trip_headsign")]
       (reset! TRIP_CACHE
               (reduce (fn [cached row]
                         (let [trip-id (get row trip-id-idx)
                               route-id (get row route-id-idx)
                               name (get row name-idx)
                               names (-> cached :dests (get route-id))
                               new-name? (or (nil? names) (> 0 (.indexOf names name)))
                               route-name-idx (if new-name?
                                                (count names) ; count nil --> 0
                                                (.indexOf names name))]
                           (cond-> cached
                               new-name?
                               (update-in [:dests route-id] (fn [names]
                                                              (if (some? names)
                                                                (conj names name)
                                                                [name])))

                               :always
                               (assoc-in [:trips trip-id] [route-id route-name-idx]))))
                       {:dests {}
                        :trips {}}
                       trips))))))

(defn- trip-cache []
  (if (some? @TRIP_CACHE)
    @TRIP_CACHE
    (load-trip-data!)))

(defn get-name [id]
  (let [data (trip-cache)
        [route-id name-idx] (get-in data [:trips id])]
    (some-> data
        :dests
        (get route-id)
        (nth name-idx))))

(comment
  (get-name "AFA21GEN-1037-Sunday-00_000600_1..S03R")
  (get-name "SIR-FA2017-SI017-Sunday-00_072100_SI..N03R")
  (get-name "SIR-FA2017-SI017-Sunday-00_072600_SI..S03R")

  (get-name "090411_A..N")
  ;
  )
