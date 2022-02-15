(ns when-is-my-ride.api
  (:require [manifold.deferred :as d]
            [when-is-my-ride.api.stop :as stop]
            [when-is-my-ride.api.stops :as stops]))

(defn stop-handler [ctx]
  (d/chain (d/future (stop/by-id (-> ctx :request :path-params :stop-id)))
           #(update-in ctx [:response] assoc :body %)))

(defn stops-handler [ctx]
  (d/chain (d/future (stops/stops-for (-> ctx :request :query-params (get "query"))))
           #(update-in ctx [:response :body]
                       assoc :stops %)))
