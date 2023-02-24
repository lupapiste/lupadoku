(ns lupadoku.routes
  (:require [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.util.response :refer [response resource-response redirect]]
            [compojure.core :refer [routes defroutes GET POST wrap-routes context]]
            [lupadoku.search :as search]
            [lupadoku.routing :as routing]
            [compojure.route :as route]
            [lupapiste-commons.ring.session :as rs]
            [lupapiste-commons.ring.session-timeout :as st]
            [lupapiste-commons.ring.utils :as ru]
            [lupapiste-commons.ring.autologin :as auto]
            [search-commons.authorization :as authorization]
            [lupadoku.municipalities :as municipalities]
            [lupadoku.onkalo-search :as onkalo]
            [lupadoku.organization :as organization]
            [lupapiste-commons.route-utils :as route-utils]
            [clojure.set :as set]
            [muuntaja.middleware :as mm]
            [monger.core :as mg]
            [taoensso.timbre :as timbre]
            [cognitect.transit :as transit])
  (:import (org.apache.commons.io IOUtils)
           (java.nio.charset StandardCharsets)))

(def authorized-roles
  #{:authority :reader})

(defn get-organizations [{:keys [orgAuthz]}]
  (map key (filter (fn [[_ roles]] (not-empty (set/intersection authorized-roles roles)))
                   orgAuthz)))

(def unauthorized {:status 401
                   :body "Unauthorized"})

(def bad-request {:status 400
                  :body "Bad Request"})

(defn- user-and-config-map [db arkisto cdn-host user]
  (let [org-ids (get-organizations user)
        organizations (organization/organization-data-by-id db org-ids)
        coordinates (municipalities/find-default-map-coordinates-for-organization db (last org-ids))
        onkalo-enabled? (boolean
                          (and (:host arkisto)
                               (some :permanent-archive-enabled (vals organizations))))
        response-map {:user   (-> (select-keys user [:firstName :lastName])
                                  (merge {:organizations           organizations
                                          :default-map-coordinates coordinates}))
                      :config {:cdn-host           cdn-host
                               :lupapiste-enabled? true
                               :onkalo-enabled?    onkalo-enabled?}}]
    (response response-map)))


(defn app-routes [{:keys [db conn arkisto cdn-host]}]
  (-> (routes (POST "/search" {body :body-params user :user}
                (response (search/search db (get-organizations user) body)))
              (POST "/search-onkalo" {body :body-params user :user}
                (onkalo/search arkisto (get-organizations user) body))
              (GET "/onkalo-download/:org-id/:doc-id" {{:keys [org-id doc-id download]} :params user :user}
                (if ((set (get-organizations user)) (keyword org-id))
                  (onkalo/get-file arkisto org-id doc-id download)
                  unauthorized))
              (POST "/mass-download" request
                (redirect "/api/raw/mass-download" :temporary-redirect))
              (GET "/onkalo-preview/:org-id/:doc-id" {{:keys [org-id doc-id]} :params user :user}
                (if ((set (get-organizations user)) (keyword org-id))
                  (onkalo/preview-image arkisto org-id doc-id)
                  unauthorized))
              (GET "/operations" {user :user}
                (response (search/orgs-operations db (get-organizations user))))
              (GET "/user-and-config" {user :user}
                (user-and-config-map db arkisto cdn-host user)))
      (wrap-routes wrap-keyword-params)
      (wrap-routes wrap-params)))

(def index-redirect (GET "/" [] (redirect routing/root)))

(defn status-route [build-info]
  (GET "/status" [] (response build-info)))

(defn index-route [translations build-info]
  (-> (GET "/" []
        (route-utils/process-index-response build-info))
      (authorization/wrap-user-authorization translations authorized-roles "/document-search")))

(defn i18n-route [translations]
  (GET "/i18n/:lang" [lang]
    (response (get translations (keyword lang) {}))))

(defn create-routes [{:keys [translations build-info autologin-check-url] :as opts}]
  (let [store (rs/rekeyable (:session-key-path opts))
        handler (-> (routes index-redirect
                            (context routing/root []
                              (status-route build-info)
                              (index-route translations build-info)
                              (-> (routes (app-routes opts)
                                          (i18n-route translations)
                                          (route/resources "/" {:mime-types {"js" "text/javascript; charset=utf-8"}}))
                                  (authorization/wrap-user-authorization translations authorized-roles))))
                    (mm/wrap-format)
                    (ru/optional-middleware ru/wrap-request-logging (= (:mode opts) :prod))
                    (auto/wrap-sso-autologin autologin-check-url)
                    st/wrap-session-timeout
                    (wrap-session {:store store})
                    ru/wrap-exception-logging)]
    (if-let [middleware (:middleware opts)]
      (middleware handler)
      handler)))
