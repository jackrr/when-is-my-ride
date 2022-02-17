(ns when-is-my-ride.perf
  (:require [systemic.core :refer [defsys]]
            [taoensso.tufte :as tufte]
            [when-is-my-ride.env :refer [env]]))

(def LOG_INTERVAL 10000)

(defonce stats-acc
  (tufte/add-accumulating-handler! {:ns-pattern "*"}))

(defn do-every [ms f]
  (future (while true (do (Thread/sleep ms)
                          (f)))))

(defn log-perf-stats []
  (when-let [stats (not-empty @stats-acc)]
    (println (tufte/format-grouped-pstats stats))))

(defsys *perf* []
  :closure
  (let [enabled? (env "LOG_PERF")
        logger (when enabled? (do-every LOG_INTERVAL log-perf-stats))]
    {:value logger
     :stop #(when logger (future-cancel logger))}))
