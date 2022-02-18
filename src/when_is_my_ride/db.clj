;; Exposes a read-only Datascript interface to transit data
;; Encapsulates logic to keep transit data up-to-date from GTFS feeds
(ns when-is-my-ride.db
  (:require [datascript.core :as ds]
            [when-is-my-ride.db.oven :as oven]))

(def ensure-hot oven/ensure-hot)
(def ensure-hot-for oven/ensure-hot-for)

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
    (apply (partial ds/q query (oven/get-db)) args)
    (ds/q query (oven/get-db))))

(defn pull-many [& args]
  (apply (partial ds/pull-many (oven/get-db)) args))

(defn pull [& args]
  (apply (partial ds/pull (oven/get-db)) args))

(comment
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
