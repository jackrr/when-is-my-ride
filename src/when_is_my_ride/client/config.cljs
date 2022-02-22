(ns when-is-my-ride.client.config
  (:require [reagent.core :as r]))

(defn meta-changed? [m1 m2]
  (not (and
       (every? (fn [key]
                 (= (key m1) (key m2)))
               (keys m1))
       (every? (fn [key]
                 (= (key m1) (key m2)))
               (keys m2)))))

(defn site-meta [_]
  (let [prev-meta (r/atom {})]
    (fn [meta]
      (when (meta-changed? @prev-meta meta)
        (when-let [title (:title meta)]
          (set! (.-title js/document) title))
        (when-let [description (:description meta)]
          (set! (.-description js/document) description))
        (reset! prev-meta meta))
      nil)))

(comment
  (meta-changed? {:title "a"} {:title "a"})
  (meta-changed? {:title "a"} {:title "a" :description "b"})
  (meta-changed? {:title "a" :description "b"} {:title "a" :description "b"})
  ;
  )
