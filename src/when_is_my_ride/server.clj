(ns when-is-my-ride.server
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :refer [error]]
            [reitit.dev.pretty :as pretty]
            [reitit.http :as http]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.interceptor.sieppari]
            [reitit.ring :as ring]
            [sieppari.async.manifold]
            [ring.adapter.jetty :as jetty]
            [muuntaja.interceptor]
            [systemic.core :as systemic :refer [defsys]]
            [taoensso.tufte :as tufte]
            [when-is-my-ride.api :as api]
            [when-is-my-ride.db :as db]
            [when-is-my-ride.cors :as cors]
            [when-is-my-ride.env :refer [env env-seconds-duration]]
            [when-is-my-ride.perf :refer [*perf*]]
            [manifold.deferred :as d]))

(defn app []
  (http/ring-handler
   (http/router
    ["/api"
     {:exception pretty/exception
      :interceptors [(parameters/parameters-interceptor)
                     (exception/exception-interceptor)
                     {:error (fn [ctx]
                               (error (:error ctx)))}
                     (cors/interceptor {:access-control-allow-origin [(re-pattern (env "ALLOW_ORIGIN"))]
                                        :access-control-allow-methods [:get :put :post :delete]})
                     {:enter (fn [ctx]
                               (db/ensure-hot-for
                                (or (env-seconds-duration "DB_KEEP_HOT_SECONDS")
                                    (* 5 60 1000)))
                               ctx)}]}
     ["/stops"
      ["" {:get {:interceptors [{:leave
                                 (fn [& args]
                                   (tufte/profile {:id "stops"
                                                   :dynamic? true}
                                                  (apply api/stops-handler args)))}]}}]
      ["/:stop-id"
       {:get {:interceptors [{:leave
                              (fn [& args]
                                (tufte/profile {:id "arrivals"
                                                :dynamic? true}
                                               (apply api/stop-handler args)))}]}}]]])
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler
     {:not-found
      ;; HACK: SPA server experience
      (constantly {:status 200
                   :body (-> "public/index.html"
                             io/resource
                             slurp)})}))
   {:executor reitit.interceptor.sieppari/executor
    :interceptors [(muuntaja.interceptor/format-interceptor)]}))

(defsys *server* []
  :deps [*perf*]
  :closure
  (let [server (jetty/run-jetty (app) {:port 3000, :join? false, :async? true})]
    (d/future (db/ensure-hot))
    {:value server
     :stop (fn [] (.stop server))}))
