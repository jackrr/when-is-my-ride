(ns when-is-my-ride.api
  (:require [manifold.deferred :as d]
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
    (->> (db/q '[:find ?station-id ?station-name ?match-id ?match-name
                :in $ ?name-like %
                :where
                [?s :name ?match-name]
                [?s :stop/id ?match-id]
                [(re-find ?name-like ?match-name)]
                (parent ?p ?s)
                [(missing? $ ?p :parent)]
                [?p :name ?station-name]
                [?p :stop/id ?station-id]]
              (re-pattern (str "(?i)" query))
              db/rules)
        (map (fn [entry]
           {:id (first entry)
            :name (nth entry 1)
            :match-id (nth entry 2)
            :match-name (last entry)}))
        (distinct-p :id)
        (sort-by :name))))

(defn stops-handler [ctx]
  (d/chain (stops-for (-> ctx :request :query-params (get "query")))
           #(update-in ctx [:response :body]
                       assoc :stops %)))

(comment
  (distinct-p :id [{:id 1 :v "one"} {:id 2 :v "two"} {:id 1 :v "another 1"}])
  @(stops-for "junc")
  @(stops-for "broad")
  ; )
  )
