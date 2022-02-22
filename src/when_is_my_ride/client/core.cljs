(ns when-is-my-ride.client.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [when-is-my-ride.client.app :as app]
   [when-is-my-ride.client.db :as db]))

(re-frame/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [app/root] root-el)))

(defn init []
  (re-frame/dispatch-sync [::initialize-db])
  (mount-root))
