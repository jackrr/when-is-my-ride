(ns when-is-my-ride.api.stops
  (:require [clojure.string :as str]
            [when-is-my-ride.api.serializers :as serializers]
            [when-is-my-ride.db :as db]
            [when-is-my-ride.util :refer [collect-deep distinct-p]]))

(def ^:private number-variants
  ;; [n digit ordinal-digit word ordinal-word]
  [[1  "1"  "1st"  "one"       "first"]
   [2  "2"  "2nd"  "two"       "second"]
   [3  "3"  "3rd"  "three"     "third"]
   [4  "4"  "4th"  "four"      "fourth"]
   [5  "5"  "5th"  "five"      "fifth"]
   [6  "6"  "6th"  "six"       "sixth"]
   [7  "7"  "7th"  "seven"     "seventh"]
   [8  "8"  "8th"  "eight"     "eighth"]
   [9  "9"  "9th"  "nine"      "ninth"]
   [10 "10" "10th" "ten"       "tenth"]
   [11 "11" "11th" "eleven"    "eleventh"]
   [12 "12" "12th" "twelve"    "twelfth"]
   [13 "13" "13th" "thirteen"  "thirteenth"]
   [14 "14" "14th" "fourteen"  "fourteenth"]
   [15 "15" "15th" "fifteen"   "fifteenth"]
   [16 "16" "16th" "sixteen"   "sixteenth"]
   [17 "17" "17th" "seventeen" "seventeenth"]
   [18 "18" "18th" "eighteen"  "eighteenth"]
   [19 "19" "19th" "nineteen"  "nineteenth"]
   [20 "20" "20th" "twenty"    "twentieth"]])

(def ^:private street-type-variants
  [["av" "ave" "avenue"]
   ["st" "street"]
   ["blvd" "boulevard"]
   ["sq" "square"]
   ["pk" "park"]
   ["pkwy" "parkway"]
   ["ctr" "center" "centre"]
   ["hts" "heights"]
   ["rd" "road"]
   ["dr" "drive"]
   ["pl" "place"]
   ["ln" "lane"]
   ["ct" "court"]
   ["hwy" "highway"]
   ["tpke" "turnpike"]])

(def ^:private direction-variants
  [["n" "north"]
   ["s" "south"]
   ["e" "east"]
   ["w" "west"]
   ["ne" "northeast"]
   ["nw" "northwest"]
   ["se" "southeast"]
   ["sw" "southwest"]])

(defn- build-expansions [variant-groups]
  (reduce (fn [m forms]
            (let [pattern (->> forms
                               (sort-by (comp - count))
                               (str/join "|")
                               (format "(?:%s)"))]
              (reduce #(assoc %1 (str/lower-case %2) pattern) m forms)))
          {}
          variant-groups))

;; Combined lookup: lowercase token -> regex alternation string
(def ^:private expansions
  (merge (build-expansions (map rest number-variants))
         (build-expansions street-type-variants)
         (build-expansions direction-variants)))

(defn- expand-query
  "Expands number/street-type/direction tokens to regex alternations that match
  all equivalent forms, then relaxes spaces to also match - and / separators.
  e.g. '4th ave' -> '(?:fourth|4th|four|4)[\\s\\-/]+(?:avenue|ave|av)'"
  [query]
  (-> query
      (str/replace #"\w+" #(get expansions (str/lower-case %) %))
      (str/replace #" +" "[\\s\\-/]+")))

(defn stops-for [query]
  (let [entities (->> (db/q
                       {:stale-ok? true}
                       '[:find ?root ?match-id ?match-name
                              :in $ ?name-like %
                              :where
                              [?s :name ?match-name]
                              [?s :stop/id ?match-id]
                              [(re-find ?name-like ?match-name)]
                              (root ?s ?root)]
                            (re-pattern (str "(?i)" (expand-query query)))
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
          {:stale-ok? true}
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
                                (collect-deep :_parent :routes)
                                (distinct-p :route/id)
                                (map serializers/route))})))
         (sort-by :name))))

(comment
  (expand-query "4th ave")         ;; => "(?:fourth|4th|four|4)[\\s\\-/]+(?:avenue|ave|av)"
  (expand-query "w 14th st")       ;; => "(?:west|w)[\\s\\-/]+(?:fourteenth|14th|fourteen|14)[\\s\\-/]+(?:street|st)"
  (expand-query "14 st union sq")  ;; => "(?:fourteenth|14th|fourteen|14)[\\s\\-/]+(?:street|st)[\\s\\-/]+union[\\s\\-/]+(?:square|sq)"
  (distinct-p :id [{:id 1 :v "one"} {:id 2 :v "two"} {:id 1 :v "another 1"}])
  (stops-for "junc")
  (stops-for "broad")
  (stops-for "dumbo")
  ; )
  )
