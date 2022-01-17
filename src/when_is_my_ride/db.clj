;; Exposes a read-only Datascript interface to transit data
;; Encapsulates logic to keep transit data up-to-date from GTFS feeds
(ns when-is-my-ride.db
  (:require [datascript.core :as d]
            [when-is-my-ride.db.mta :as mta]))

(def schema
  {:complex/id {:db/cardinality :db.cardinality/one
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

(defn- new-conn []
  (d/create-conn schema))

(def ^:private conn (d/create-conn schema))

(defn- refresh-db! []
  (let [next (new-conn)]
    (mta/load-all next)
    (d/reset-conn! conn @next))
  true)

(defn- get-db []
  ;; TODO: ensure up to date within 30s, if not, call refresh-db!
  @conn)

(defn q [query]
  (d/q query (get-db)))

(comment
  (refresh-db!)
  (q '[:find ?stop ?at
       :where
       [?r :route/id "A"]
       [?ts :trip-stop/route ?r]
       [?ts :trip-stop/stop ?s]
       [?s :stop/id ?stop]
       [?ts :trip-stop/at ?at]])
  ; ensure autoformatter preserves \n before )
  )
