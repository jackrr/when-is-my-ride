(ns when-is-my-ride.core
  (:gen-class)
  (:require [systemic.core :as systemic]
            [when-is-my-ride.server]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (systemic/start!))
