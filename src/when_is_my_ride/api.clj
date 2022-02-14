(ns when-is-my-ride.api
  (:require [datascript.core :as ds]
            [manifold.deferred :as d]
            [when-is-my-ride.db :as db]))

(defn distinct-p
  ([pred coll]
   (distinct-p pred coll #{}))
  ([pred coll seen]
   (if (empty? coll)
     '()
     (lazy-seq
      (let [next (first coll)
            key (pred next)
            is-dup (contains? seen key)
            recurred (distinct-p pred (rest coll) (if is-dup seen (conj seen key)))]
        (if is-dup
          recurred
          (cons next recurred)))))))

(defn stops-for [query]
  (d/future
    (let [entities (->> (db/q '[:find ?root ?match-id ?match-name
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
                                  ;; recursively find routes
                                  ((fn rr [s]
                                     (let [routes (or (:routes s) [])]
                                       (if (some? (:_parent s))
                                         (concat routes
                                                 (->> s
                                                      :_parent
                                                      (map rr)
                                                      flatten))
                                         routes))))
                                  ;; simplify keys
                                  (map (fn [r] (-> r
                                                   (assoc :agency (-> r :agency :agency/id)
                                                          :id (:route/id r))
                                                   (dissoc :route/id))))
                                  (distinct-p :id))})))
           (sort-by :name)))))

(defn stops-handler [ctx]
  (d/chain (stops-for (-> ctx :request :query-params (get "query")))
           #(update-in ctx [:response :body]
                       assoc :stops %)))

(comment
  (distinct-p :id [{:id 1 :v "one"} {:id 2 :v "two"} {:id 1 :v "another 1"}])
  @(stops-for "junc")
  @(stops-for "broad")
  @(stops-for "dumbo")
  ; )
  )
