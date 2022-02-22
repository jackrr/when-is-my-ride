(ns when-is-my-ride.db.oven
  (:require [clojure.tools.logging :refer [debug info]]
            [datascript.core :as ds]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [when-is-my-ride.db.mta :as mta]
            [when-is-my-ride.db.nyc-ferry :as nyc-ferry]
            [when-is-my-ride.env :refer [env-seconds-duration]]
            [when-is-my-ride.db.schema :refer [schema]]
            [when-is-my-ride.util :refer [do-every]]
            [taoensso.tufte :as tufte]))

(def STALE_THRESHOLD (or (env-seconds-duration "DB_STALE_THRESHOLD_SECONDS")
                         (* 1000 50)))

(def ^:private static (atom nil))

(def ^:private THE_CONN (ds/create-conn schema))

(defn- load-into [conn loaders]
  (let [query (fn [& args] (apply (partial ds/q (first args) @conn) (rest args)))
        txns (s/stream)
        to-insert (s/buffer count 1500 txns)
        insertion-streams (map (fn [loader]
                                 (let [stream (s/stream)]
                                   (s/connect stream txns {:downstream? false})
                                   (d/future (loader stream query))
                                   stream)) loaders)
        count (atom 0)]
    (while (not (and (every? s/drained? insertion-streams)
                     (= 0 (-> to-insert s/description :buffer-size))))
      (let [msg @(s/try-take! to-insert ::drained 1000 ::timeout)]
        (cond (= msg ::drained)
              (debug "to-insert buffer drained")

              (= msg ::timeout)
              (info "Timeout on to-insert buffer")

              :else
              (ds/transact! conn msg))
        (when (= (mod @count 500) 0)
          (debug "Processed " @count " txns"))
        (swap! count inc)))))

(defn- static-db
  "Getter for pre-loaded static db. Blocks and loads if not already loaded."
  []
  (if (some? @static)
    @static
    (do
      (info "Initializing static data")
      (let [conn (ds/create-conn schema)]
        (load-into conn [mta/load-static nyc-ferry/load-static])
        (reset! static @conn)
        (info "Done initializing static data")
        @conn))))

(defn- refresh-db! []
  (info "Reloading DB")
  (d/future
    (tufte/p ::refresh-db!
             (let [next (ds/conn-from-db (static-db))]
               (load-into next [mta/load-realtime nyc-ferry/load-realtime])
               (ds/transact! next [{:initialized-at (System/currentTimeMillis)}])
               (ds/reset-conn! THE_CONN @next)
               (info "DB refresh complete")))))

(defn- cold?
  ([] (cold? STALE_THRESHOLD))
  ([threshold]
   (let [last-initialized
         (some-> (ds/q '[:find (max ?iat)
                         :where
                         [_ :initialized-at ?iat]]
                       @THE_CONN)
                 first
                 first)]
     (or (not last-initialized)
         (< (+ last-initialized threshold) (System/currentTimeMillis))))))

(defn- initialized? []
  (some? (some-> (ds/q '[:find (max ?iat)
                         :where
                         [_ :initialized-at ?iat]]
                       @THE_CONN)
                 first
                 first)))

(def ^:private hot-lock (Object.))

(defn ensure-hot
  ([] (ensure-hot STALE_THRESHOLD))
  ([threshold]
   (locking hot-lock
     (when (cold? threshold) @(refresh-db!)))))

(defn get-db
  "Provide conn to db of transit data, block on refresh if older than threshold"
  ([] (get-db {}))
  ([{:keys [stale-ok?]}]
   (if stale-ok?
     (when (not (initialized?)) (ensure-hot))
     (when cold? (ensure-hot)))
   @THE_CONN))

(def ^:private keep-hot-interval (atom nil))
(def ^:private keep-hot-killer (atom nil))

(defn ensure-hot-for [duration]
  (let [stop-current! (fn []
                        (when @keep-hot-interval
                          (future-cancel @keep-hot-interval)
                          (reset! keep-hot-interval nil)))]
    (stop-current!)
    (when @keep-hot-killer (future-cancel @keep-hot-killer))
    (reset! keep-hot-interval
            (let [refresh-interval (/ STALE_THRESHOLD 2)]
              (do-every refresh-interval
                        (fn []
                          (info "Ensuring hot")
                          (ensure-hot refresh-interval)))))
    (reset! keep-hot-killer
            (future
              (Thread/sleep duration)
              (stop-current!)))))

(comment
  @(refresh-db!)

  (tufte/add-basic-println-handler! {:format-pstats-opts {:columns [:n-calls :p50 :mean :clock :total]}})
  (tufte/profile
   {:dynamic? true}
   (dotimes [_ 2]
     @(refresh-db!)))

  ;
  )
