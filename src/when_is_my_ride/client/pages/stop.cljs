(ns when-is-my-ride.client.pages.stop
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [when-is-my-ride.client.api :as api]
            [when-is-my-ride.client.route :as route]
            [when-is-my-ride.client.ui :as ui]))

(def default-db {::loading true
                 :arrivals []
                 :routes {}})

(def ^:private default-shown 3)

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

(defn- format-direction [direction]
  (case direction
    "NORTH" "Northbound"
    "SOUTH" "Southbound"
    "EAST"  "Eastbound"
    "WEST"  "Westbound"
    direction))

(defn- display-dest [{:keys [destination headsign]}]
  (or (not-empty destination)
      (not-empty headsign)))

(rf/reg-sub ::direction-groups
            (fn [_ _] (rf/subscribe [::arrivals]))
            (fn [arrivals _]
              (let [multi-stop? (->> arrivals (map :stop-id) distinct count (< 1))
                    unique-names? (->> arrivals (map :stop-name) distinct count (< 1))]
                (->> arrivals
                     (group-by (juxt :direction :stop-id))
                     (map (fn [[[direction stop-id] group]]
                            (let [terminals (->> group (keep display-dest) distinct sort)
                                  stop-name (:stop-name (first group))]
                              {:direction       direction
                               :stop-id         stop-id
                               :stop-name       stop-name
                               :show-stop-name? (and multi-stop? unique-names?)
                               :route-ids       (->> group (map :route-id) distinct sort)
                               :terminal-label  (if (and (seq terminals) (<= (count terminals) 2))
                                                  (str "to " (str/join " / " terminals))
                                                  (format-direction direction))})))
                     (sort-by (juxt :stop-id :direction))))))

(rf/reg-sub ::next-n-arrivals
            (fn [_ _] [(rf/subscribe [::routes])
                       (rf/subscribe [::arrivals])])
            (fn [[routes arrivals] [_ n direction stop-id]]
              (cond->> arrivals
                :always
                (sort-by :at)
                (some? direction)
                (filter #(and (= (:direction %) direction)
                              (= (:stop-id %) stop-id)))
                :always
                (take n)
                :always
                (map (fn [arr] (assoc arr :route (get routes (:route-id arr))))))))

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

(defn arrival [now {:keys [id route at destination headsign]}]
  [:div {:key id :className "my-2 flex gap-3 items-center"}
   (route/icon route)
   (when-let [dest (display-dest {:destination destination :headsign headsign})]
     [:p {:className "flex-1 text-sm"} (str "to " dest)])
   [:p {:className "text-sm tabular-nums"}
    (let [min (-> at (* 1000) (- now) (/ (* 60 1000)) js/Math.floor)]
      (if (> 1 min) "Now" (str min " min")))]])

(defn platform-card [{:keys [direction stop-id stop-name show-stop-name? route-ids terminal-label]}]
  (let [expanded? (r/atom false)]
    (fn []
      (let [now (.valueOf (js/Date.))
            routes @(rf/subscribe [::routes])
            all-arrivals @(rf/subscribe [::next-n-arrivals 20 direction stop-id])
            shown (if @expanded? all-arrivals (take default-shown all-arrivals))]
        [:div {:className "border border-gray-200 rounded-lg p-4"}
         [:div {:className "mb-2 pb-2 border-b border-gray-200"}
          (when show-stop-name?
            [:p {:className "text-xs text-gray-500 mb-1"} stop-name])
          [:div {:className "flex items-center gap-2 flex-wrap"}
           (for [rid route-ids]
             ^{:key rid} (route/icon (get routes rid)))
           [:span {:className "text-sm font-medium"} terminal-label]]]
         (if (empty? all-arrivals)
           [:p {:className "italic text-sm"} "No upcoming departures"]
           [:div
            (map (partial arrival now) shown)
            (when (> (count all-arrivals) default-shown)
              [:button {:className "text-sm text-blue-600 mt-2"
                        :on-click #(swap! expanded? not)}
               (if @expanded? "Show less ↑" "See more ↓")])])]))))

(defn view [_]
  [:div {:className "container mx-auto max-w-6xl px-4"}
   [:h2 {:className "text-xl mb-4"} @(rf/subscribe [::stop-name])]
   (if @(rf/subscribe [::loading])
     [ui/loading]
     (let [groups @(rf/subscribe [::direction-groups])]
       (if (empty? groups)
         [:p {:className "italic"} "No upcoming departures found"]
         [:div {:className "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"}
          (for [{:keys [direction stop-id] :as group} groups]
            ^{:key (str direction stop-id)}
            [platform-card group])])))])

(def route {:name :stop
            :path "/stops/:stop-id"
            :view view
            :on-load (fn [{:keys [params]}]
                       (rf/dispatch [::load-stop (:stop-id params)]))})
