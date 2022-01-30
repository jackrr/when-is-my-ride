(ns when-is-my-ride.client.app
  (:require [when-is-my-ride.client.api]
            [when-is-my-ride.client.stops :as stops]
            [when-is-my-ride.client.ui]))

(defn root []
  [:div {:className "bg-gray"}
   [:h1 {:className "my-2 text-center text-xl"} "When is my ride?"]
   (stops/search)])
