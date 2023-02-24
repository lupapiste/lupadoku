(ns lupadoku.onkalo-search
  (:require [org.httpkit.client :as http]
            [cemerick.url :refer [url-encode]]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]
            [clojure.string :as s]
            [clj-time.coerce :as c]
            [clj-time.format :as f]
            [lupapiste-commons.tos-metadata-schema :as tms]
            [search-commons.geo-conversion :as geo]))

(def s2-metadata-keys
  (set (map #(or (:k %) %) (keys tms/AsiakirjaMetaDataMap))))

(def result-limit 500)

(defn- process-result [{:keys [metadata] :as result}]
  (let [non-s2-keys (remove #(s2-metadata-keys %) (keys metadata))
        application-id (:applicationId metadata)]
    (-> (merge result (select-keys metadata non-s2-keys))
        (assoc :metadata (apply dissoc metadata non-s2-keys)
               :id (:fileId result)
               :source-system :onkalo
               :grouping-key (if-not (s/blank? application-id) application-id (first (:kuntalupatunnukset metadata)))
               :location (:location-etrs-tm35fin metadata)
               :operations (set (:operations metadata))
               :kayttotarkoitukset (set (:kayttotarkoitukset metadata)))
        (dissoc :fileId :location-etrs-tm35fin))))

(defn- send-request [{:keys [host app-id app-key]} query]
  (let [start (System/currentTimeMillis)
        url (str host "/documents?" query)
        options {:basic-auth [app-id app-key]}
        {:keys [status body]} @(http/get url options)]
    (if (= 200 status)
      (let [{:keys [meta results]} (json/parse-string body true)]
        {:status  200
         :body    {:results (map process-result results)
                   :has-more? (:moreResultsAvailable meta)
                   :took (float (/ (- (System/currentTimeMillis) start) 1000))}})
      (do
        (timbre/warn "Onkalo search error:" status body)
        {:status status
         :body body}))))

(defn- unparse-ts [ts]
  (when ts
    (f/unparse (:date-time-no-ms f/formatters) (c/from-long ts))))

(defn- coordinate-fields [shapes]
  (map (fn [shape]
         ["shape"
          (->> (map geo/epsg3067->wgs84 shape)
               (map #(s/join "," %))
               (s/join ";"))])
       shapes))

(defn search [config orgs {:keys [text fields timespan usage page type operation coordinates organization tokenize?]}]
  (let [from (* result-limit page)
        selected-orgs (->> (if (s/blank? organization) orgs (filter #(= % (keyword organization)) orgs))
                           (map (fn [org] ["organization" (name org)])))
        text (when text (s/trim text))
        search-fields (cond-> (conj selected-orgs ["search-from" from] ["search-limit" result-limit])
                              (and (empty? fields) (not (s/blank? text))) (conj ["all" text])
                              (:address fields) (conj ["address" text])
                              (:attachment.label.contents fields) (conj ["contents" text])
                              (:applicant fields) (conj ["applicants" text])
                              (:handler fields) (conj ["kasittelija" text])
                              (:designer fields) (conj ["suunnittelijat" text])
                              (:propertyId fields) (conj ["propertyId" text])
                              (:from timespan) (conj ["paatospvm" (str "gte:" (unparse-ts (:from timespan)))])
                              (:to timespan) (conj ["paatospvm" (str "lte:" (unparse-ts (:to timespan)))])
                              (:closed-from timespan) (conj ["closed" (str "gte:" (unparse-ts (:closed-from timespan)))])
                              (:closed-to timespan) (conj ["closed" (str "lte:" (unparse-ts (:closed-to timespan)))])
                              (:foreman fields) (conj ["foremen" text])
                              (:tyomaasta-vastaava fields) (conj ["tyomaasta-vastaava" text])
                              (:projectDescription fields) (conj ["projectDescription" text])
                              usage (conj ["kayttotarkoitukset" usage])
                              (seq type) (conj ["type" (s/join "." (map name type))])
                              operation (conj ["operations" operation])
                              (seq coordinates) (concat (coordinate-fields coordinates))
                              tokenize? (conj ["tokenize" 1]))
        query-str (->> (map (fn [[k v]] (str k "=" (url-encode v))) search-fields)
                       (s/join "&"))]
    (send-request config query-str)))

(defn get-file [{:keys [host app-id app-key]} org id download?]
  (let [url (str host "/documents/" (url-encode id))
        q   {:organization org}
        options {:basic-auth [app-id app-key]
                 :query-params q}
        {:keys [status headers body] :as resp} @(http/get url options)]
    (if (= 200 status)
      {:status status
       :body body
       :headers (cond-> {"Content-Type" (:content-type headers)}
                        download? (assoc "Content-Disposition" (str (:content-disposition headers))))}
      (do
        (timbre/error "Could not download document" id "from onkalo." (select-keys resp [:url :error]))
        {:status 500
         :body "Error occurred"}))))

(defn preview-image [{:keys [host app-id app-key]} org id]
  (let [url (str host "/documents/" (url-encode id) "/preview")
        q   {:organization org}
        options {:basic-auth [app-id app-key]
                 :query-params q}
        {:keys [status headers body]} @(http/get url options)]
    {:status status
     :body body
     :headers {"Content-Type" (:content-type headers)}}))
