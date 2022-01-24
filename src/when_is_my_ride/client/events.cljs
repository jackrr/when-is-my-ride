(ns when-is-my-ride.client.events
  (:require
   [re-frame.core :as re-frame]
   [when-is-my-ride.client.db :as db]
   ))

(re-frame/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))
