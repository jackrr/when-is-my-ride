(ns when-is-my-ride.api.stop
  (:require [when-is-my-ride.api.serializers :as serializers]
            [when-is-my-ride.db :as db]
            [when-is-my-ride.util :refer [collect-deep distinct-p]]))

(defn- arrivals [id]
  (map (fn [res]
         {:id (first res)
          :at (nth res 1)
          :route-id (nth res 2)
          :direction (nth res 3)
          :destination (let [dest (nth res 4)
                             tname (nth res 5)]
                         (if (not-empty tname) tname dest))})
       (db/q '[:find ?tsid ?at ?rid ?dir ?dest ?tname
               :in $ ?stop-id ?now ?max %
               :where
               [?s :stop/id ?stop-id]
               (self-and-children ?s ?a)
               [?ts :stop ?a]
               [?ts :at ?at]
               [(< ?now ?at)]
               [(> ?max ?at)]
               [?ts :trip-stop/id ?tsid]
               [?ts :trip ?t]
               [(get-else $ ?t :destination "") ?dest]
               [(get-else $ ?t :name "") ?tname]
               [?t :direction ?dir]
               [?ts :route ?r]
               [?r :route/id ?rid]]
             id
             (/ (System/currentTimeMillis) 1000)
             (+ (/ (System/currentTimeMillis) 1000) (* 60 60)) ; 60 min from now
             db/rules)))

(defn by-id [id]
  (let [stop (db/pull '[:stop/id
                        :name
                        {:routes [:route/id :abbr :color {:agency [:agency/id]}]}
                        {:_stop [:trip-stop/id
                                 :at
                                 {:route [:route/id]}]}
                        {:_parent ...}]
                      (first (db/q '[:find [?s]
                                     :in $ ?stop-id
                                     :where
                                     [?s :stop/id ?stop-id]]
                                   id)))]
    {:id (:stop/id stop)
     :name (:name stop)
     :routes (->> stop
                  (collect-deep :_parent :routes)
                  (distinct-p :route/id)
                  (map serializers/route))
     :arrivals (arrivals id)}))

(comment
  (by-id "nyc-ferry-20")
  (by-id "mta-cplx-619")
  ; )
  )
