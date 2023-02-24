(ns lupadoku.municipalities
  (:require [monger.collection :as mc]
            [search-commons.municipality-coords :as coords]))

(defn- user-default-municipality [db org-id]
  (when-let [org (mc/find-one-as-map db :organizations {:_id org-id} {:scope 1})]
    (when-let [code (:municipality (first (:scope org)))]
      (Integer/parseInt code))))

(defn find-default-map-coordinates-for-organization [db org-id]
  (let [municipality (user-default-municipality db org-id)]
    (coords/center-coordinates municipality)))
