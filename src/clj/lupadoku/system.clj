(ns lupadoku.system
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [lupadoku.db :as db]
            [lupadoku.http :as http]
            [search-commons.translations :as translations]))

(defn local-config-file [filename]
  (let [parts (string/split filename #"\.")]
    (str (first parts) "-local." (second parts))))

(defn deep-merge [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))

(defn read-config [filename]
  (try
    (let [config (edn/read-string (slurp filename))]
      (let [local-config (io/file (local-config-file filename))]
        (if (.exists local-config)
          (deep-merge config (edn/read-string (slurp local-config)))
          config)))
    (catch java.io.FileNotFoundException e
      (println (str "Could not find configuration file: " filename)))))

(defn new-system [config build-info]
  (let [system {:db (db/new-Db (get-in config [:db :uri]))
                :translations (translations/->Translations)
                :web (component/using (http/new-HttpServer (:http config) build-info)
                                      [:db :translations])}]
    (component/map->SystemMap system)))
