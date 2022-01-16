(ns when-is-my-ride.env
  (:require
   [clojure.java.io :as io]
   [systemic.core :refer [defsys]]))

(defsys file-env
  :start
  (-> (io/resource "secrets.edn")
      (slurp)
      (read-string)))

(defn env [key]
  (or (System/getenv key) (file-env key)))
