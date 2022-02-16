(ns when-is-my-ride.client.ui
  (:require [re-frame.core :as rf]))

(defonce timeouts (atom {}))

(rf/reg-fx :dispatch-debounce
           (fn [{:keys [id dispatch delay]}]
             (js/clearTimeout (@timeouts id))
             (swap! timeouts assoc id
                    (js/setTimeout (fn []
                                     (rf/dispatch dispatch)
                                     (swap! timeouts dissoc id))
                                   delay))))

(defn loading []
  [:p {:className "italic color-gray-400"} "Loading..."])
