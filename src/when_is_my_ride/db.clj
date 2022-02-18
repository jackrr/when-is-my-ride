;; Exposes a read-only Datascript interface to transit data
;; Encapsulates logic to keep transit data up-to-date from GTFS feeds
(ns when-is-my-ride.db
  (:require [datascript.core :as ds]
            [when-is-my-ride.db.oven :as oven]))

(defn ensure-hot [& args] (apply oven/ensure-hot args))
(defn ensure-hot-for [& args] (apply oven/ensure-hot-for args))

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

(defn- args->positionals [query-or-conf args]
  (let [has-conf? (map? query-or-conf)]
    {:conf (if has-conf? query-or-conf {})
     :query (if has-conf? (first args) query-or-conf)
     :rest (if has-conf? (rest args) args)}))

(defn q [query-or-conf & args]
  (let [{:keys [conf query rest]} (args->positionals query-or-conf args)]
    (apply (partial ds/q query (oven/get-db conf)) rest)))

(defn pull-many [query-or-conf & args]
  (let [{:keys [conf query rest]} (args->positionals query-or-conf args)]
    (apply (partial ds/pull-many (oven/get-db conf) query) rest)))

(defn pull [query-or-conf & args]
  (let [{:keys [conf query rest]} (args->positionals query-or-conf args)]
    (apply (partial ds/pull (oven/get-db conf) query) rest)))

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
