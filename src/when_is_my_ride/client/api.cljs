(ns when-is-my-ride.client.api
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]))

(goog-define API_BASE "/api")

(rf/reg-event-fx
 ::fetch
 (fn [_ [_ {:keys [path on-success]}]]
   {:http-xhrio
    {:method :get
     :uri (str API_BASE path)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success on-success}}))
