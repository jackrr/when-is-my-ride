(ns when-is-my-ride.mta
  (:import [com.google.transit.realtime NyctSubway])
  (:require [hato.client :as hc]
            [protobuf.core :as protobuf]
            [when-is-my-ride.env :as env]))

;; TODO: add a subnamespace for accessing and refreshing stop data
