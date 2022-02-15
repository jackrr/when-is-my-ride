(ns when-is-my-ride.core
  (:gen-class)
  (:require [ring.adapter.jetty :as jetty]
            [when-is-my-ride.server :as server]))

(defn -main
  "I don't do a whole lot ... yet."
  [& _]
  (jetty/run-jetty #'server/app {:port 3000, :join? false, :async? true}))
