(ns when-is-my-ride.db.schema)

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
   :destination {:db/cardinality :db.cardinality/one}
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
