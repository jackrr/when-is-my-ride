(ns when-is-my-ride.env
  (:require [clojure.java.io :as io]))

(defn file-env [key]
  (some-> (io/resource "secrets.edn")
          (slurp)
          (read-string)
          (get key)))

(defn env [key]
  (or (System/getenv key) (file-env key)))
