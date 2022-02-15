(ns when-is-my-ride.api.serializers)

(defn route [r]
  (-> r
      (assoc :agency (-> r :agency :agency/id)
             :id (:route/id r))
      (dissoc :route/id)))
