(ns when-is-my-ride.client.app
  (:require [when-is-my-ride.client.api]
            [when-is-my-ride.client.pages.search :as search]
            [when-is-my-ride.client.pages.stop :as stop]
            [when-is-my-ride.client.router :refer [router]]
            [when-is-my-ride.client.ui]))

(defn root []
  [:div {:className "bg-gray"}
   [:h1 {:className "my-2 text-center text-xl"} "When is my ride?"]
   [router {:routes [stop/route
                     search/route]
            :default-route search/route}]])
