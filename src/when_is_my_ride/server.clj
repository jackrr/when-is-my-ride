(ns when-is-my-ride.server
  (:require [reitit.http :as http]
            [reitit.ring :as ring]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.interceptor.sieppari]
            [sieppari.async.manifold]
            [ring.adapter.jetty :as jetty]
            [muuntaja.interceptor]
            [systemic.core :as systemic :refer [defsys]]
            [when-is-my-ride.api :as api]
            [when-is-my-ride.env :as env]))

(def app
  (http/ring-handler
   (http/router
    ["/api"
     {:interceptors [(parameters/parameters-interceptor)]}
     ["/stops"
      {:get {:interceptors [{:leave (fn [& args] (apply api/stops-handler args))}]}}]])

   (ring/create-default-handler)
   {:executor reitit.interceptor.sieppari/executor
    :interceptors [(muuntaja.interceptor/format-interceptor)]}))

(defsys *server* []
  :closure
  (let [server (jetty/run-jetty #'app {:port 3000, :join? false, :async? true})]
    {:value server
     :stop (fn [] (.stop server))}))
