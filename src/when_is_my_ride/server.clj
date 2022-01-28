(ns when-is-my-ride.server
  (:require [reitit.http :as http]
            [reitit.ring :as ring]
            [reitit.interceptor.sieppari]
            [sieppari.async.manifold]
            [ring.adapter.jetty :as jetty]
            [muuntaja.interceptor]
            [manifold.deferred :as d]
            [systemic.core :as systemic :refer [defsys]]
            [when-is-my-ride.env :as env]))

(defn interceptor [f x]
  {:enter (fn [ctx] (f (update-in ctx [:request :via] (fnil conj []) {:enter x})))
   :leave (fn [ctx] (f (update-in ctx [:response :body] conj {:leave x})))})

(defn handler [f]
  (fn [{:keys [via]}]
    (f {:status 200
        :body (conj via :handler)})))

(def <sync> identity)
(def <deferred> d/success-deferred)

(def app
  (http/ring-handler
   (http/router
    ["/api"
     {:interceptors [(interceptor <sync> :api)]}
     ["/sync"
      {:interceptors [(interceptor <sync> :sync)]
       :get {:interceptors [(interceptor <sync> :get)]
             :handler (handler <sync>)}}]
     ["/deferred"
      {:interceptors [(interceptor <deferred> :deferred)]
       :get {:interceptors [(interceptor <deferred> :get)]
             :handler (handler <deferred>)}}]])

   (ring/create-default-handler)
   {:executor reitit.interceptor.sieppari/executor
    :interceptors [(muuntaja.interceptor/format-interceptor)]}))

(defsys *server* []
  :deps [env/file-env]
  :closure
  (let [server (jetty/run-jetty #'app {:port 3000, :join? false, :async? true})]
    {:value server
     :stop (fn [] (.stop server))}))
