;; Exposes a read-only Datascript interface to transit data
;; Encapsulates logic to keep transit data up-to-date from GTFS feeds
(ns when-is-my-ride.db
  (:require [datascript.core :as ds]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [when-is-my-ride.db.mta :as mta]
            [when-is-my-ride.db.nyc-ferry :as nyc-ferry]
            [when-is-my-ride.db :as db]
            [taoensso.tufte :as tufte]))

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
   :trip-stop/id {:db/cardinality :db.cardinality/one
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
   :routes {:db/valueType :db.type/ref
            :db/cardinality :db.cardinality/many}
   :agency {:db/valueType :db.type/ref
            :db/cardinality :db.cardinality/one}})

(def STALE_THRESHOLD (* 1000 30))

(defn- new-conn []
  (ds/create-conn schema))

(def ^:private conn (new-conn))

(def ^:private loading-next-conn (atom nil))

(defn- refresh-db! []
  (tufte/p ::refresh-db!
           (println "Reloading DB")
           (d/future
             (let [next (new-conn)
                   query (fn [& args] (apply (partial ds/q (first args) @next) (rest args)))
                   txns (s/stream)
                   to-insert (s/buffer count 100 txns)]
               (s/consume (fn [txn]
                            (tufte/p ::refresh-db-transact
                                     (ds/transact! next txn)))
                          to-insert)
               @(d/zip
                 (d/future (mta/load-all txns query))
                 (d/future (nyc-ferry/load-all txns query)))
               (ds/transact! next [{:initialized-at (System/currentTimeMillis)}])
               (ds/reset-conn! conn @next)
               (println "DB refresh complete")))))

(defn get-db
  "Provide conn to db of transit data, trigger an async refresh if older than threshold"
  []
  (d/chain
   @loading-next-conn
   (fn [_]
     (let [last-initialized
           (some-> (ds/q '[:find (max ?iat)
                           :where
                           [_ :initialized-at ?iat]]
                         @conn)
                   first
                   first)]
       (when (or (not last-initialized)
                 (< (+ last-initialized STALE_THRESHOLD) (System/currentTimeMillis)))
         (let [deferred (refresh-db!)]
           (reset! loading-next-conn deferred)
           @(d/chain deferred
                     (fn [_]
                       (reset! loading-next-conn nil))))))))
  @conn)

(def rules '[[(self ?e1 ?e2) [(identity ?e1) ?e2]]
             [(parent ?p ?c) (?c :parent ?p)]
             [(parent ?p ?c) (?c :parent ?p1) (parent ?p ?p1)]
             [(self-and-children ?s ?a) (or (self ?s ?a)
                                            (parent ?s ?a))]
             [(root? ?p) [(missing? $ ?p :parent)]]
             [(root ?e ?r) (and
                            (or (parent ?r ?e)
                                (self ?e ?r))
                            (root? ?r))]])

(defn q [query & args]
  (if args
    (apply (partial ds/q query (get-db)) args)
    (ds/q query (get-db))))

(defn pull-many [& args]
  (apply (partial ds/pull-many (get-db)) args))

(defn pull [& args]
  (apply (partial ds/pull (get-db)) args))

(comment
  (refresh-db!)

  (tufte/add-basic-println-handler! {})
  (tufte/profile
   {:dynamic? true}
   (dotimes [_ 1]
     @(refresh-db!)))

  ;; Datascript does not store transaction timestamps
  (q '[:find ?tx-time
       :where
       [_ _ _ ?tx]
       [?tx :db/txInstant ?tx-time]])

  (q '[:find ?r
       :where
       [_ :route/id ?r]])
  (q '[:find ?r ?name
       :where
       [?r :abbr "ER"]
       [?r :name ?name]])
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
       [?r :abbr "A"]
       [?ts :route ?r]
       [?ts :stop ?s]
       [?s :stop/id ?stop]
       [?ts :at ?at]])
  ; ensure autoformatter preserves \n before )
  )
