(ns when-is-my-ride.client.pages.search
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [when-is-my-ride.client.api :as api]
            [when-is-my-ride.client.route :as route]
            [when-is-my-ride.client.router :refer [navigate]]
            [when-is-my-ride.client.ui :as ui]))

(rf/reg-sub ::search-results (fn [db]
                               (or (get-in db [::stops ::search-results]) [])))
(rf/reg-sub ::loading (fn [db]
                        (or
                         (get-in db [::stops ::loading])
                         false)))

(rf/reg-event-db
 ::handle-search-results
 (fn [db [_ {:keys [stops]}]]
   (-> db
       (assoc-in [::stops ::loading] false)
       (assoc-in [::stops ::search-results]
                 (map (fn [s] {::id (:id s)
                               ::name (:name s)
                               ::routes (:routes s)
                               ::match {::id (:match-id s)
                                        ::name (:match-name s)}}) stops)))))

(rf/reg-event-fx
 ::search
 (fn [{:keys [db]} [_ query]]
   (if (and (some? query) (> (count query) 2))
     {:dispatch-debounce {:id ::stops-search
                          :delay 150
                          :dispatch [::api/fetch {:path (str "/stops?query=" query)
                                                  :on-success [::handle-search-results]}]}
      :db (-> db
              (assoc-in [::stops ::loading] true)
              (update-in [::stops] dissoc ::search-results))}
     {})))

(defn search [_]
  [:div {:className "p-3 rounded-xl shadow-lg max-w-md mx-auto border-bottom h-screen flex flex-col"}
   [:label "Enter station name:"]
   [:input {:className "my-2 px-2 rounded-md border"
            :on-change #(rf/dispatch [::search (-> % .-target .-value)])}]
   [:div {:className "overflow-y-auto"}
    (when @(rf/subscribe [::loading]) [ui/loading])
    [:div
     (map
      (fn [r] [:a {:key (::id r)
                   :className "flex gap-x-1 my-2 cursor-pointer hover:bg-gray-200"
                   :on-click #(navigate {:to (str "/stops/" (::id r))})}
               (let [name (::name r)
                     match-name (get-in r [::match ::name])]
                 [:p (str name
                          (when (not (str/includes? name match-name)) (str " (" match-name  ")")))])
               (map route/icon (::routes r))])
      @(rf/subscribe [::search-results]))]]])

(def route {:name :search
            :path "/"
            :view search})
