(ns lupadoku.http
 (:require [com.stuartsierra.component :as component]
           [org.httpkit.server :refer [run-server]]
           [monger.core :as mg]
           [lupadoku.routes :as routes]
           [taoensso.timbre :as timbre]))

(defn start-http-server [component {:keys [port] :as config}]
  (let [stop-fn (run-server (routes/create-routes config) {:port port
                                                           :thread 100})]
    (timbre/info (str "HTTP server started on port " port))
    (assoc component :server-stop stop-fn)))

(defn stop-http-server [component server-stop]
  (server-stop)
  (timbre/info "HTTP server stopped")
  (assoc component :server-stop nil))

(defrecord HttpServer [config build-info db translations]
  component/Lifecycle
  (start [this]
    (if (:server-stop this)
      this
      (do
        (start-http-server this (assoc config
                                       :db (get-in db [:mongo :db])
                                       :conn (get-in db [:mongo :conn])
                                       :build-info build-info
                                       :translations (:data translations))))))
  (stop [this]
    (when-let [server-stop (:server-stop this)]
      (stop-http-server this server-stop))
    this))

(defn new-HttpServer [config build-info]
  (map->HttpServer {:config config
                    :build-info build-info}))
