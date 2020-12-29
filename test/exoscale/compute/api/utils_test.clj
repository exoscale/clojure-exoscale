(ns exoscale.compute.api.utils-test
  (:require [ring.adapter.jetty             :refer [run-jetty]]
            [ring.middleware.json           :refer [wrap-json-response
                                                    wrap-json-params]]
            [ring.middleware.params         :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]))

(defn make-app [handlers]
  (fn [request]
    (let [{:keys [params]} request
          command (->> params :command keyword)
          handler (get handlers command)]
      (if handler
        (handler params)
        (throw (ex-info "Command not implemented" {:command command
                                                   :params params}))))))

(defmacro with-http
  [port handlers & body]
  `(let [app# (-> (make-app ~handlers)
                  wrap-keyword-params
                  wrap-json-params
                  wrap-params
                  wrap-json-response)

         server# (run-jetty app# {:port ~port :join? false})]
     (try
       ~@body
       (finally
         (.stop server#)))))
