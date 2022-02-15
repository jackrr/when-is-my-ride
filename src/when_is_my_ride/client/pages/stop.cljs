(ns when-is-my-ride.client.pages.stop)

(defn view [{:keys [params]}]
  (println params)
  [:div "Stop"])

(def route {:name :stop
            :path "/stops/:stop-id"
            :view view})
