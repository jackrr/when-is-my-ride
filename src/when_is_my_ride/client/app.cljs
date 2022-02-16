(ns when-is-my-ride.client.app
  (:require [when-is-my-ride.client.api]
            [when-is-my-ride.client.pages.search :as search]
            [when-is-my-ride.client.pages.stop :as stop]
            [when-is-my-ride.client.router :refer [router navigate]]
            [when-is-my-ride.client.ui]))

(defn root []
  [:div {:className "bg-gray"}
   [:div {:className "my-2 container flex items-end mx-auto"}
    [:div {:className "flex-1 w-16"}]
    [:h1 {:className "flex-grow text-center text-xl"}
     [:a {:on-click #(navigate {:to "/"})} "When is my ride?"]]
    [:a {:className "flex-1 w-16 underline"
         :rel "noopener noreferrer"
         :target "_blank"
         :href "https://forms.gle/cJBChxnjzYotF68Y9"} "Feedback"]]
   [router {:routes [stop/route
                     search/route]
            :default-route search/route}]])
