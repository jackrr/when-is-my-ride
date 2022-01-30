(ns when-is-my-ride.cors
  (:require [ring.middleware.cors :as cors]))

(defn interceptor [access-control]
  (when access-control
    (let [access-control (cors/normalize-config (mapcat identity access-control))]
      {:enter (fn cors-interceptor-enter
                [{:keys [request] :as ctx}]
                (if (and (cors/preflight? request)
                         (cors/allow-request? request access-control))
                  (let [resp (cors/add-access-control
                              request
                              access-control
                              cors/preflight-complete-response)]
                    (assoc ctx
                           :response resp
                           :queue nil))
                  ctx))
       :leave (fn cors-interceptor-leave
                [{:keys [request response] :as ctx}]
                (cond-> ctx
                  (and (cors/origin request)
                       (cors/allow-request? request access-control)
                       response)
                  (assoc :response
                         (cors/add-access-control
                          request
                          access-control
                          response))))})))
