(ns when-is-my-ride.client.pages.stop
  (:require [re-frame.core :as rf]
            [when-is-my-ride.client.api :as api]
            [when-is-my-ride.client.route :as route]
            [when-is-my-ride.client.ui :as ui]))

(def default-db {::loading true
                 :arrivals []
                 :routes {}})

(rf/reg-sub ::stop (fn [{::keys [stop]}] (or stop default-db)))
(rf/reg-sub ::loading
            (fn [_ _] (rf/subscribe [::stop]))
            (fn [stop _] (::loading stop)))
(rf/reg-sub ::stop-name
            (fn [_ _] (rf/subscribe [::stop]))
            (fn [stop _] (:name stop)))
(rf/reg-sub ::arrivals
            (fn [_ _] (rf/subscribe [::stop]))
            (fn [stop _] (:arrivals stop)))
(rf/reg-sub ::routes
            (fn [_ _] (rf/subscribe [::stop]))
            (fn [stop _] (:routes stop)))
(rf/reg-sub ::all-directions
            (fn [_ _] (rf/subscribe [::arrivals]))
            (fn [arrivals _]
              (->> arrivals
                   (map :direction)
                   distinct
                   sort)))
(rf/reg-sub ::next-n-arrivals
            (fn [_ _] [(rf/subscribe [::routes])
                       (rf/subscribe [::arrivals])])
            (fn [[routes arrivals] [_ n & args]]
              (let [direction (first args)]
                (cond->> arrivals
                  :always
                  (sort-by :at)
                  (some? direction)
                  (filter #(= (:direction %) direction))
                  :always
                  (take n)
                  :always
                  (map (fn [arr] (assoc arr :route (get routes (:route-id arr)))))))))

(rf/reg-event-db
 ::handle-stop-result
 (fn [db [_ stop]]
   (-> db
       (assoc ::stop {:id (:id stop)
                      :name (:name stop)
                      :routes (reduce (fn [rs r]
                                        (assoc rs (:id r) r)) {} (:routes stop))
                      :arrivals (:arrivals stop)}))))

(rf/reg-event-fx
 ::load-stop
 (fn [{:keys [db]} [_ stop-id]]
   {:dispatch [::api/fetch {:path (str "/stops/" stop-id)
                            :on-success [::handle-stop-result]}]
    :db (assoc db ::stop default-db)}))

(defn arrival [now {:keys [id route at destination direction]}]
  [:div {:key id :className "my-2 flex gap-4"}
   (route/icon route)
   [:p (if (not-empty destination) destination direction)]
   [:p (let [min (-> at
                     (* 1000)
                     (- now)
                     (/ (* 60 1000))
                     (js/Math.floor))]
         (if (> 1 min) "Now" (str min " min")))]])

(defn view [_]
  [:div {:className "container mx-auto max-w-md px-4"}
   [:h2 {:className "text-xl mb-4"} @(rf/subscribe [::stop-name])]
   [:div {:className "flex justify-between"}
    (if @(rf/subscribe [::loading])
      [ui/loading]
      (let [none-found [:p {:className "italic"} "No upcoming departures found"]
            all (doall
                 (map (fn [direction]
                        [:div {:key direction}
                         [:h3 direction]
                         (let [arrivals @(rf/subscribe [::next-n-arrivals 20 direction])]
                           (if (= 0 (count arrivals))
                             none-found
                             (map (partial arrival (.valueOf (js/Date.))) arrivals)))])
                      @(rf/subscribe [::all-directions])))]
        (if (empty? all) none-found all)))]])

(def route {:name :stop
            :path "/stops/:stop-id"
            :view view
            :on-load (fn [{:keys [params]}]
                       (rf/dispatch [::load-stop (:stop-id params)]))})
