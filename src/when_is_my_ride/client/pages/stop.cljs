(ns when-is-my-ride.client.pages.stop
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [when-is-my-ride.client.api :as api]))

(rf/reg-event-db
 ::handle-stop-result
 (fn [db [_ data]]
   (println "Got stop data" data)))

(rf/reg-event-fx
 ::load-stop
 (fn [_ [_ stop-id]]
   {:dispatch [::api/fetch {:path (str "/stops/" stop-id)
                            :on-success [::handle-stop-result]}]}))

(defn view [_]
  [:div "Stop"])

(def route {:name :stop
            :path "/stops/:stop-id"
            :view view
            :on-load (fn [{:keys [params]}]
                       (println params)
                       (rf/dispatch [::load-stop (:stop-id params)])) })
