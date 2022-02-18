(ns when-is-my-ride.db.oven
  (:require [datascript.core :as ds]
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

(defn new-empty []
  (ds/create-conn schema))

(def ^:private conn (new-empty))

(defn- refresh-db! []
  (println "Reloading DB")
  (d/future
    (tufte/p ::refresh-db!
             (let [next (new-empty)
                   query (fn [& args] (apply (partial ds/q (first args) @next) (rest args)))
                   mta-txns (s/stream)
                   ferry-txns (s/stream)
                   txns (s/stream)
                   to-insert (s/buffer count 1500 txns)]
               (s/connect mta-txns txns {:downstream? false})
               (s/connect ferry-txns txns {:downstream? false})
               (d/future (mta/load-all mta-txns query))
               (d/future (nyc-ferry/load-all ferry-txns query))
               (let [count (atom 0)]
                 (while (not (and (s/drained? mta-txns)
                                  (s/drained? ferry-txns)
                                  (= 0 (-> to-insert s/description :buffer-size))))
                   (let [msg @(s/try-take! to-insert ::drained 1000 ::timeout)]
                     (swap! count inc)
                     (when (= (mod @count 500) 0)
                       (println "Processed " @count " txns"))

                     (cond
                       (= msg ::drained)
                       (println msg)

                       (= msg ::timeout)
                       (println msg)

                       :else
                       (ds/transact! next msg))))
                 (println "Processed " @count " txns"))

               (ds/transact! next [{:initialized-at (System/currentTimeMillis)}])
               (ds/reset-conn! conn @next)
               (println "DB refresh complete")))))

(def ^:private loading-next-conn (atom nil))

(defn ensure-hot
  ([] (ensure-hot STALE_THRESHOLD))
  ([threshold]
   (println "Start of ensure hot")
   (d/chain
    @loading-next-conn
    (fn [_]
      (println "Have lock, refreshing")
      (let [last-initialized
            (some-> (ds/q '[:find (max ?iat)
                            :where
                            [_ :initialized-at ?iat]]
                          @conn)
                    first
                    first)]
        (when (or (not last-initialized)
                  (< (+ last-initialized threshold) (System/currentTimeMillis)))
          (let [deferred (refresh-db!)]
            (reset! loading-next-conn deferred)
            @(d/chain deferred
                      (fn [_]
                        (reset! loading-next-conn nil))))))))))

(defn get-db
  "Provide conn to db of transit data, block on refresh if older than threshold"
  []
  (println "In get-db")
  (ensure-hot)
  (println "Done waiting for db refresh")
  @conn)

(def ^:private hot-interval (atom nil))

(defn ensure-hot-for [duration]
  (let [stop-current! (fn []
                        (when @hot-interval
                          (future-cancel @hot-interval)
                          (reset! hot-interval nil)))]
    (stop-current!)
    (reset! hot-interval
            (let [refresh-interval (/ STALE_THRESHOLD 2)]
              (do-every #(ensure-hot refresh-interval) refresh-interval)))
    (d/future
      (Thread/sleep duration)
      (stop-current!))))

(comment
  (refresh-db!)

  (tufte/add-basic-println-handler! {:format-pstats-opts {:columns [:n-calls :p50 :mean :clock :total]}})
  (tufte/profile
   {:dynamic? true}
   (dotimes [_ 2]
     @(refresh-db!)))

  ;
  )
