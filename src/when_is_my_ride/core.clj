(ns when-is-my-ride.core
  (:gen-class)
  (:require [ring.adapter.jetty :as jetty]
            [when-is-my-ride.server :as server]))

(defn -main
  [& _]
  (jetty/run-jetty (server/app) {:port 3000, :join? false, :async? true}))
