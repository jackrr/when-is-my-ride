(ns when-is-my-ride.api.stops
  (:require [when-is-my-ride.api.serializers :as serializers]
            [when-is-my-ride.db :as db]
            [when-is-my-ride.util :refer [collect-deep distinct-p]]))

(defn stops-for [query]
  (let [entities (->> (db/q
                       {:stale-ok? true}
                       '[:find ?root ?match-id ?match-name
                              :in $ ?name-like %
                              :where
                              [?s :name ?match-name]
                              [?s :stop/id ?match-id]
                              [(re-find ?name-like ?match-name)]
                              (root ?s ?root)]
                            (re-pattern (str "(?i)" query))
                            db/rules)
                      (map (fn [entry]
                             {:eid (first entry)
                              :match-id (nth entry 1)
                              :match-name (nth entry 2)}))
                      (distinct-p :eid))
        entity-lookup (reduce (fn [lookup match] (assoc lookup (:eid match) match)) {} entities)]
    ; pull route info and name for each stop
    (->> entities
         (map :eid)
         (db/pull-many
          {:stale-ok? true}
          '[:db/id
            :stop/id
            :name
            {:routes
             [:route/id :abbr :color {:agency [:agency/id]}]}
            {:_parent ...}])
         (map (fn [stop]
                (let [match (get entity-lookup (:db/id stop))]
                  {:id (:stop/id stop)
                   :name (:name stop)
                   :match-id (:match-id match)
                   :match-name (:match-name match)
                   :routes (->> stop
                                (collect-deep :_parent :routes)
                                (distinct-p :route/id)
                                (map serializers/route))})))
         (sort-by :name))))

(comment
  (distinct-p :id [{:id 1 :v "one"} {:id 2 :v "two"} {:id 1 :v "another 1"}])
  (stops-for "junc")
  (stops-for "broad")
  (stops-for "dumbo")
  ; )
  )
