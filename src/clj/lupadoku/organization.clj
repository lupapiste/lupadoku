(ns lupadoku.organization
  (:require [monger.collection :as mc]
            [monger.operators :refer :all]))

(defn- permit-types [scopes]
  (set (map :permitType scopes)))

(defn organization-data-by-id [db org-ids]
  (->> (mc/find-maps db "organizations" {:_id {$in org-ids}})
       (reduce
         (fn [orgs {:keys [_id name scope permanent-archive-enabled]}]
           (assoc orgs _id {:name name
                            :permit-types (permit-types scope)
                            :permanent-archive-enabled permanent-archive-enabled}))
         {})))
