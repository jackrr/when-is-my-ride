(ns when-is-my-ride.api
  (:require [manifold.deferred :as d]
            [when-is-my-ride.db :as db]))

(defn stops-for [query]
  (d/future
    (map
     (fn [entry]
       {:id (first entry)
        :name (last entry)})
     (db/q '[:find ?id ?name
             :in $ ?name-like
             :where
             [?s :name ?name]
             [?s :stop/id ?id]
             [(re-find ?name-like ?name)]]
           (re-pattern (str "(?i)" query))))))

(defn stops-handler [ctx]
  (d/chain (stops-for (-> ctx :request :query-params (get "query")))
           #(update-in ctx [:response :body]
                       conj {:stops %})))

(comment
  @(stops-for "broadway")
  ; )
  )
