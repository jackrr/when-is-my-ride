(ns when-is-my-ride.client.api
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]))

(rf/reg-event-fx
 ::fetch
 (fn [_ [_ {:keys [path on-success]}]]
   {:http-xhrio
    {:method :get
     :uri (str "http://localhost:3000/api" path)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success on-success}}))
