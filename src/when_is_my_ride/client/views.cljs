(ns when-is-my-ride.client.views
  (:require
   [re-frame.core :as re-frame]
   [when-is-my-ride.client.subs :as subs]
   ))

(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div {:className " bg-gray-50"}
     [:h1
      "Hello from " @name]
     [:h2 "Another heading"]
     ]))
