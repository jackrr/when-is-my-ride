(ns when-is-my-ride.core
  (:gen-class)
  (:require [systemic.core :as systemic]
            [when-is-my-ride.server]))

(defn -main
  [& _]
  (println "Booting app...")
  (systemic/start!))
