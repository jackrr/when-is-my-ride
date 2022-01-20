;; Exposes a read-only Datascript interface to transit data
;; Encapsulates logic to keep transit data up-to-date from GTFS feeds
(ns when-is-my-ride.db
  (:require [datascript.core :as d]
            [when-is-my-ride.db.mta :as mta]))

(def schema
  {:initialized-at {:db/cardinality :db.cardinality/one}
   :complex/id {:db/cardinality :db.cardinality/one
                :db/unique :db.unique/identity}
   :complex/name {:db/cardinality :db.cardinality/one}

   :stop/id {:db/cardinality :db.cardinality/one
             :db/unique :db.unique/identity}

   :stop/name {:db/cardinality :db.cardinality/one}
   :stop/complex {:db/valueType :db.type/ref
                  :db/cardinality :db.cardinality/one}
   :stop/parent {:db/valueType :db.type/ref
                 :db/cardinality :db.cardinality/one}

   :route/id {:db/cardinality :db.cardinality/one
              :db/unique :db.unique/identity}
   :route/name {:db/cardinality :db.cardinality/one}
   :route/type {:db/cardinality :db.cardinality/one}

   :trip/id {:db/cardinality :db.cardinality/one
             :db/unique :db.unique/identity}
   :trip/route {:db/valueType :db.type/ref
                :db/cardinality :db.cardinality/one}
   :trip/direction {:db/cardinality :db.cardinality/one}

   :trip-stop/stop {:db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/one}
   :trip-stop/trip {:db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/one}
   :trip-stop/route {:db/valueType :db.type/ref
                     :db/cardinality :db.cardinality/one}
   :trip-stop/at {:db/cardinality :db.cardinality/one}})

(def STALE_THRESHOLD (* 1000 30))

(defn- new-conn []
  (d/create-conn schema))

(def ^:private conn (new-conn))

(defn- refresh-db! []
  (let [next (new-conn)]
    (mta/load-all next)
    (d/transact! next [{:initialized-at (System/currentTimeMillis)}])
    (d/reset-conn! conn @next))
  true)

(defn- get-db
  "Provide conn to db of transit data, refreshed if older than threshold"
  []
  (let [last-initialized
        (some-> (d/q '[:find (max ?iat)
                       :where
                       [_ :initialized-at ?iat]]
                     @conn)
                first
                first)]
    (when (or (not last-initialized)
              (< (+ last-initialized STALE_THRESHOLD) (System/currentTimeMillis)))
      (refresh-db!)))
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
       [?r :route/id ?name]]
     "A")
  (q '[:find ?stop ?at
       :where
       [?r :route/id "A"]
       [?ts :trip-stop/route ?r]
       [?ts :trip-stop/stop ?s]
       [?s :stop/id ?stop]
       [?ts :trip-stop/at ?at]])
  ; ensure autoformatter preserves \n before )
  )
