(ns lupadoku.logging
  (:require [clojure.string :as s]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [timbre-json-appender.core :as tas]))

; Timbre

(def time-format "yyyy-MM-dd HH:mm:ss.SSS")

(defn- output-fn
  "Logging output function"
  ([data] (output-fn {:stacktrace-fonts {}} data))
  ([opts data]
   (let [{:keys [level ?err msg_ ?ns-str hostname_ timestamp_ ?line]} data]
     (str
       (force timestamp_) \space
       (-> level name s/upper-case) \space
       "[" (or ?ns-str "?") ":" (or ?line "?") "] - "
       (force msg_)
       (when ?err (str "\n" (timbre/stacktrace ?err opts)))))))

(defn configure-logging! [config]
  (-> (if (:file config)
        {:level          (:level config)
         :timestamp-opts {:pattern  time-format
                          :timezone :jvm-default}
         :output-fn      output-fn
         :appenders      {:spit (appenders/spit-appender {:fname (:file config)})}}
        ;; Log JSON to stdout if file not defined
        {:min-level (or (:level config) :info)
         :appenders {:json (tas/json-appender {:inline-args? true
                                               :level-key    :severity
                                               :msg-key      :message})}})
      (timbre/set-config!)))
