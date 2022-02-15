(ns when-is-my-ride.client.pages.stop
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [when-is-my-ride.client.api :as api]
            [when-is-my-ride.client.route :as route]))

(rf/reg-sub ::stop (fn [{::keys [stop]}] (or stop {:arrivals []
                                                   :routes {}})))
(rf/reg-sub ::stop-name
            (fn [_ _] (rf/subscribe [::stop]))
            (fn [stop _] (:name stop)))
(rf/reg-sub ::arrivals
            (fn [_ _] (rf/subscribe [::stop]))
            (fn [stop _] (:arrivals stop)))
(rf/reg-sub ::routes
            (fn [_ _] (rf/subscribe [::stop]))
            (fn [stop _] (:routes stop)))
(rf/reg-sub ::next-n-arrivals
            (fn [_ _] [(rf/subscribe [::routes])
                       (rf/subscribe [::arrivals])])
            (fn [[routes arrivals] [_ n]]
              (->> arrivals
                  (sort-by :at)
                  (take n)
                  (map (fn [arr] (assoc arr :route (get routes (:route-id arr))))))))

(rf/reg-event-db
 ::handle-stop-result
 (fn [db [_ stop]]
   (assoc db ::stop {:id (:id stop)
                     :name (:name stop)
                     :routes (reduce (fn [rs r]
                                       (assoc rs (:id r) r)) {} (:routes stop))
                     :arrivals (:arrivals stop)})))

(rf/reg-event-fx
 ::load-stop
 (fn [_ [_ stop-id]]
   {:dispatch [::api/fetch {:path (str "/stops/" stop-id)
                            :on-success [::handle-stop-result]}]}))

(defn arrival [now {:keys [id route at direction]}]
  [:div {:key id :className "my-2 flex gap-4"}
   (route/icon route)
   [:p direction]
   [:p (let [min (-> at
                    (* 1000)
                    (- now)
                    (/ (* 60 1000))
                    (js/Math.floor))]
         (if (> 1 min) "Now" (str min " min")))]])

(defn view [_]
  [:div {:className "mx-auto max-w-md"}
   [:h2 {:className "text-xl mb-4"} @(rf/subscribe [::stop-name])]
   (map (partial arrival (.valueOf (js/Date.))) @(rf/subscribe [::next-n-arrivals 20]))])

(def route {:name :stop
            :path "/stops/:stop-id"
            :view view
            :on-load (fn [{:keys [params]}]
                       (rf/dispatch [::load-stop (:stop-id params)])) })
