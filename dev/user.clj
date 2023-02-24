(ns user
  (:require [reloaded.repl :refer [system init start stop go reset]]
            [lupadoku.system :as system]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [clojure.tools.namespace.repl :refer [refresh]]))

(reloaded.repl/set-init! #(system/new-system (system/read-config "lupadoku-config.edn")
                                             {:build "devel"}))
