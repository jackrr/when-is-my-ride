(ns when-is-my-ride.client.route)

(defn icon [{:keys [abbr color id]}]
  [:div {:key id
         :className "rounded-full text-white text-center font-bold w-6 h-6"
         :style {:background-color (str "#" (if (not-empty color) color "AAAAAA"))}}
   abbr])
