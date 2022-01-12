(ns when-is-my-ride.core
  (:gen-class)
  (:require [when-is-my-ride.server :as server]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (server/start))
