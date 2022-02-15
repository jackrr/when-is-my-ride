(ns when-is-my-ride.client.router
  (:require [reagent.core :as r]
            [clojure.string :as str]))

(def ^:private nav-event-id "ROUTER::NAV")

(defn- find-first [f coll]
  (first (filter f coll)))

(defn- path-utils [path]
  (let [segment? (fn [seg] (str/starts-with? seg ":"))
        segs (str/split path #"/")
        segs (if (empty? (first segs)) (rest segs) segs)
        url-seg "([A-z0-9\\-_]+)"]
    {:segments segs
     :params (filter segment? segs)
     :matcher (if (empty? segs)
                (re-pattern "^\\/$")
                (re-pattern
                 (str "^\\/?"
                      (str/join "\\/" (map (fn [seg]
                                             (if (segment? seg) url-seg seg)) segs))
                      "$")))}))

(defn- expand-route [route]
  (merge route (path-utils (:path route))))

(defn- handle-new-loc! [routes default-route state loc]
  (let [path (:path loc)
        route (find-first (fn [{:keys [matcher]}]
                            (re-matches matcher path)) routes)
        route (or route default-route)
        path-match (re-matches (:matcher route) path)]
    (swap! state (fn [_] {:view (:view route)
                          :name (:name route)
                          :path (:path loc)
                          :params (if (< 0 (count path-match))
                                    (zipmap (:params route) (rest path-match))
                                    {})
                          ;; TODO: implement query string parsing once needed
                          :query {}}))))

(defn- nav-event []
  (js/CustomEvent. nav-event-id {}))

(defn navigate [{:keys [to]}]
  (.pushState js/window.history {} nil to)
  (.dispatchEvent js/window (nav-event)))

(defn router [{:keys [routes default-route]}]
  (let [handle-nav! (partial handle-new-loc!
                             (map expand-route routes)
                             (expand-route default-route))
        nav (r/atom {})
        on-nav (fn [& _]
                 (let [loc (.-location js/window)]
                   (handle-nav! nav {:path (.-pathname loc)
                                     :search (.-search loc)})))]
    (r/create-class
     {:display-name "router"
      :component-did-mount (fn [_]
                             (on-nav)
                             (.addEventListener js/window nav-event-id on-nav)
                             (.addEventListener js/window "popstate" on-nav))
      :component-will-unmount (fn [_]
                                (.removeEventListener js/window nav-event-id on-nav)
                                (.removeEventListener js/window "popstate" on-nav))
      :reagent-render
      (fn []
        (let [{:keys [view] :as nav} @nav]
          (when view
            [view (dissoc nav :view)])))})))
