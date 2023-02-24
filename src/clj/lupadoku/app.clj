(ns lupadoku.app
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [lupadoku.logging :as logging]
            [lupadoku.system :as system]
            [lupapiste-commons.utils :refer [get-build-info]]
            [taoensso.timbre :as timbre]))

(def app-system (atom nil))

(defn -main [& args]
  (when-let [config (system/read-config (or (first args) "lupadoku-config.edn"))]
    (logging/configure-logging! (:logging config))
    (timbre/info "Starting app")
    (try
      (let [build-info (get-build-info "lupadoku.jar")]
        (doseq [[k v] build-info]
          (timbre/info (str k ": " v)))
        (let [system (component/start (system/new-system config build-info))]
          (reset! app-system system)
          (.addShutdownHook (Runtime/getRuntime) (java.lang.Thread. #(component/stop system)))))
      (catch Throwable t
        (let [message "Error while starting application"]
          (timbre/error t message)
          (println (str message ": " t)))
        (System/exit 1)))))
