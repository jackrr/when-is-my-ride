;; Exposes a read-only Datascript interface to transit data
;; Encapsulates logic to keep transit data up-to-date from GTFS feeds
(ns when-is-my-ride.db
  (:require [datascript.core :as d]
            [when-is-my-ride.db.mta :as mta]
            [when-is-my-ride.db.nyc-ferry :as nyc-ferry]))

(def schema
  {:initialized-at {:db/cardinality :db.cardinality/one}
   :stop/id {:db/cardinality :db.cardinality/one
             :db/unique :db.unique/identity}
   :route/id {:db/cardinality :db.cardinality/one
              :db/unique :db.unique/identity}
   :trip/id {:db/cardinality :db.cardinality/one
             :db/unique :db.unique/identity}
   :agency/id {:db/cardinality :db.cardinality/one
               :db/unique :db.unique/identity}

   :abbr {:db/cardinality :db.cardinality/one}
   :name {:db/cardinality :db.cardinality/one}
   :type {:db/cardinality :db.cardinality/one}
   :color {:db/cardinality :db.cardinality/one}
   :direction {:db/cardinality :db.cardinality/one}
   :at {:db/cardinality :db.cardinality/one}

   :stop {:db/valueType :db.type/ref
          :db/cardinality :db.cardinality/one}
   :parent {:db/valueType :db.type/ref
            :db/cardinality :db.cardinality/one}
   :trip {:db/valueType :db.type/ref
          :db/cardinality :db.cardinality/one}
   :route {:db/valueType :db.type/ref
           :db/cardinality :db.cardinality/one}
   :agency {:db/valueType :db.type/ref
            :db/cardinality :db.cardinality/one}})

(def STALE_THRESHOLD (* 1000 30))

(defn- new-conn []
  (d/create-conn schema))

(def ^:private conn (new-conn))

(defn- refresh-db! []
  (let [next (new-conn)]
    (mta/load-all next)
    (nyc-ferry/load-all next)
    (d/transact! next [{:initialized-at (System/currentTimeMillis)}])
    (d/reset-conn! conn @next))
  true)

(defn- get-db
  "Provide conn to db of transit data, trigger an async refresh if older than threshold"
  []
  (let [last-initialized
        (some-> (d/q '[:find (max ?iat)
                       :where
                       [_ :initialized-at ?iat]]
                     @conn)
                first
                first)]
    (cond
      (not last-initialized)
      ;; Need to initialize the db
      (refresh-db!)

      (< (+ last-initialized STALE_THRESHOLD) (System/currentTimeMillis))
      (future (refresh-db!))))
  @conn)

(defn q [query & args]
  (if args
    (apply (partial d/q query (get-db)) args)
    (d/q query (get-db))))

(comment
  (refresh-db!)

  ;; Datascript does not store transaction timestamps
  (q '[:find ?tx-time
       :where
       [_ _ _ ?tx]
       [?tx :db/txInstant ?tx-time]])

  (q '[:find ?r
       :where
       [_ :route/id ?r]])
  (q '[:find ?r
       :where
       [?r :route/id "A"]])
  (every?
   (fn [route]
     (not-empty
      (q '[:find ?r
           :in $ ?name
           :where
           [?r :route/id ?name]]
         route)))
   ["A" "C" "E"])
  (q '[:find ?r
       :where
       [_ :route/id ?r]])
  (q '[:find ?r
       :in $ ?name
       :where
       [?r :abbr ?name]
       [?r :route/id ?id]]
     "A")
  (q '[:find ?stop ?at
       :where
       [?r :route/id "A"]
       [?ts :route ?r]
       [?ts :stop ?s]
       [?s :stop/id ?stop]
       [?ts :at ?at]])
  ; ensure autoformatter preserves \n before )
  )
