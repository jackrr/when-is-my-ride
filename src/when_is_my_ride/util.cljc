(ns when-is-my-ride.util)

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

(defn find-first [f coll]
  (first (filter f coll)))

(defn collect-deep [child-key collect-key thing]
  (let [collected (or (get thing collect-key) '())]
    (if-let [child-list (get thing child-key)]
      (concat collected
              (flatten (map (partial collect-deep child-key collect-key) child-list)))
      collected)))

(comment
  (collect-deep :children :things
                {:things [1 2]
                 :children [{:things [3 4]}
                            {:things [5 6]
                             :children [{:things [7 8]}
                                        {:things [9]}]}]})
  ; )
  )
