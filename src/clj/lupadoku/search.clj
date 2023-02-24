(ns lupadoku.search
  (:require [clojure.string :as s]
            [clojure.pprint :refer [cl-format]]
            [taoensso.timbre :as timbre]
            [search-commons.domain :as domain]
            [search-commons.utils :as utils]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [search-commons.geo-conversion :as geo]))

(def limit 500)

(defn include-field? [fields field]
  (or (empty? fields) (contains? fields field)))

(defn- foreman-application-ids [orgs foreman-search-text db]
  (->> (mc/find-maps db
                     "applications"
                     {:organization {$in orgs}
                      :primaryOperation.name "tyonjohtajan-nimeaminen-v2"
                      :foreman foreman-search-text}
                     [:_id])
       (map :_id)))

(defn- foremen-and-their-project-applications
  "First finds foreman application IDs, where 'text' is foreman.
  Then fetches linked project application IDs for those foreman applications.
  Returns collection of distinct concatenation of those IDs."
  [orgs text db]
  (let [foreman-application-ids (foreman-application-ids orgs text db)
        links (mc/find-maps db "app-links" {:link {$in foreman-application-ids}})]
    (distinct (mapcat #(:link %) links))))

(defn- address-ends-with-number? [value]
  (some? (re-matches #".+\s[0-9]+\S*" value)))

(defn- exact-address-match [token]
  (if (address-ends-with-number? token)
    (str token "$")
    token))

(defn- address-reg-exp [tokens]
  (->> tokens
       (map #(s/replace % "\"" ""))
       (map exact-address-match)
       (s/join "|")))

(defn with-fields [orgs fields text tokenize? db]
  (let [tokens (if tokenize? (s/split text #"\s(?=([^\"]*\"[^\"]*\")*[^\"]*$)") [text])
        reg-exp (if tokenize?
                  (->> tokens (map #(str "(" (s/replace % "\"" "") ")")) (s/join "|"))
                  text)
        property-id-reg-exp (utils/property-id-regexp-string tokens)
        text-regex {$regex reg-exp $options "i"}
        address-query-reg-exp {$regex (address-reg-exp tokens) $options "i"}]
    (cond-> [{:_id text}
             {:propertyId (utils/zero-padded-property-id text)}
             {:verdicts.kuntalupatunnus text}
             {:buildings.nationalId text}
             {:buildings.localId text}]
            (and (include-field? fields :propertyId) property-id-reg-exp) (conj {:propertyId {$regex property-id-reg-exp $options "i"}})
            (include-field? fields :address) (conj {:address address-query-reg-exp})
            (include-field? fields :applicant) (conj {"_applicantIndex" text-regex})
            (include-field? fields :attachment.label.contents) (conj {"attachments.contents" text-regex})
            (include-field? fields :handler) (conj {"authority.firstName" text-regex})
            (include-field? fields :handler) (conj {"authority.lastName" text-regex})
            (include-field? fields :designer) (conj {"_designerIndex" text-regex})
            (include-field? fields :foreman) (conj {:_id {$in (foremen-and-their-project-applications orgs text-regex db)}})
            (include-field? fields :tyomaasta-vastaava) (conj {$and [{:documents.schema-info.name "tyomaastaVastaava"}
                                                                     {$or [{:documents.data.henkilo.henkilotiedot.sukunimi.value text-regex}
                                                                           {:documents.data.henkilo.henkilotiedot.etunimi.value text-regex}]}]})
            (include-field? fields :projectDescription) (conj {"_projectDescriptionIndex" text-regex}))))

(defn- doc-has-usage? [doc]
  (and (get-in doc [:schema-info :op :id])
       (get-in doc [:data :kaytto :kayttotarkoitus :value])))

(defn set-usage-type [{:keys [documents attachments] :as result}]
  (let [id-to-usage (->> documents
                         (filter doc-has-usage?)
                         (map (fn [{:keys [schema-info data]}]
                                [(get-in schema-info [:op :id]) (get-in data [:kaytto :kayttotarkoitus :value])]))
                         (into {}))]
    (->> (if-let [attachment-op-id (get-in attachments [:op :id])]
           #{(get id-to-usage attachment-op-id)}
           (set (vals id-to-usage)))
         (assoc result :kayttotarkoitukset))))

(defn set-operation [doc]
  (let [primary-op {(get-in doc [:primaryOperation :id]) (get-in doc [:primaryOperation :name])}
        id-operation-map (conj primary-op (into {} (map (fn [op] [(:id op) (:name op)]) (:secondaryOperations doc))))
        attachment-op-id (get-in doc [:attachments :op :id])
        ops (set (if attachment-op-id [(get id-operation-map attachment-op-id)] (vals id-operation-map)))]
    (assoc doc :operations ops)))

(defn set-verdict-date [doc]
  (let [verdict-dates (->> (:verdicts doc)
                           (map (fn [verdict]
                                  (->> (:paatokset verdict)
                                       (map #(->> (:poytakirjat %)
                                                  (map :paatospvm)
                                                  (remove nil?))))))
                           (flatten))
        ts (when (seq verdict-dates) (apply max verdict-dates))]
    (assoc doc :verdict-ts ts)))

(defn flatten-attachment-data [doc]
  (->> (get-in doc [:attachments :latestVersion])
       (merge (dissoc (:attachments doc) :latestVersion))
       (merge (dissoc doc :attachments))))

(defn- set-buildings-by-operation [{:keys [attachments buildings documents] :as doc}]
  (let [op-ids (map :id (:op attachments))
        op-ids-contain? (fn [id] (some #(= id %) op-ids))
        op-filtered-bldgs (filter #(op-ids-contain? (:operationId %)) buildings)
        op-filtered-docs (filter #(op-ids-contain? (get-in % [:schema-info :op :id])) documents)
        bids1 (remove #(s/blank? %) (map :localId op-filtered-bldgs))
        bids2 (remove #(s/blank? %) (map #(get-in % [:data :kunnanSisainenPysyvaRakennusnumero :value]) op-filtered-docs))
        nbids1 (remove #(s/blank? %) (map :nationalId op-filtered-bldgs))
        nbids2 (remove #(s/blank? %) (map #(get-in % [:data :valtakunnallinenNumero :value]) op-filtered-docs))]
    (assoc doc :buildingIds (or (seq bids1) bids2) :nationalBuildingIds (or (seq nbids1) nbids2))))

(defn- set-kuntalupatunnukset [{:keys [verdicts] :as doc}]
  (->> verdicts
       (map :kuntalupatunnus)
       (assoc doc :kuntalupatunnukset)))

(defn- filter-by-national-id [id results]
  (filter (fn [{:keys [buildings]}]
            (some #(= id (:nationalId %)) buildings))
          results))

(def vtj-prt-check-chars
  ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "A" "B" "C" "D" "E" "F"
   "H" "J" "K" "L" "M" "N" "P" "R" "S" "T" "U" "V" "W" "X" "Y"])

(defn- is-vtj-prt? [text]
  (when-not (s/blank? text)
    (let [[_ number check-char] (re-matches #"(\d{9})(\w)" text)]
      (when number
        (= (s/upper-case check-char) (nth vtj-prt-check-chars (mod (Integer/parseInt number) 31)))))))

(defn- coords->multipolygon [etrs-polygons]
  (map (fn [polygon]
         [(map #(geo/epsg3067->wgs84 %) polygon)])
       etrs-polygons))

(defn- geo-query [coordinates]
  (let [wgs84-multipolygon (coords->multipolygon coordinates)
        location-queries (map #(-> {:location-wgs84 {$geoWithin {"$polygon" (first %)}}}) wgs84-multipolygon)]
    {$or (conj location-queries
               {:drawings.geometry-wgs84 {$geoIntersects {"$geometry" {:type "MultiPolygon" :coordinates wgs84-multipolygon}}}})}))


(defn search [db orgs {:keys [text fields timespan usage page type operation coordinates organization tokenize?]}]
  (let [trim-text (s/trim (or text ""))
        start (System/currentTimeMillis)]
    (if (domain/search-disabled? trim-text coordinates)
      []
      (let [selected-orgs (if (s/blank? organization) orgs (filter #(= % (keyword organization)) orgs))
            [type-group type-id] type
            {:keys [from to closed-from closed-to]} timespan
            query [{$match {$and (cond-> [{:state        {$nin ["canceled" "draft"]}
                                           :organization {$in selected-orgs}
                                           :permitType   {$ne "ARK"}}]
                                         (not (s/blank? trim-text)) (conj {$or (with-fields selected-orgs fields trim-text tokenize? db)})
                                         type-group (conj {:attachments.type.type-group type-group})
                                         type-id (conj {:attachments.type.type-id type-id})
                                         from (conj {$or [{:submitted {$gte from}} {:verdicts.paatokset.poytakirjat.paatospvm {$gte from}}]})
                                         to (conj {$or [{:submitted {$lte to}} {:verdicts.paatokset.poytakirjat.paatospvm {$lte to}}]})
                                         closed-from (conj {:closed {$gte closed-from}})
                                         closed-to (conj {:closed {$lte closed-to}})
                                         usage (conj {:documents.data.kaytto.kayttotarkoitus.value usage})
                                         operation (conj {$or [{:primaryOperation.name operation} {:secondaryOperations.name operation}]})
                                         (seq coordinates) (conj (geo-query coordinates)))}}
                   {$unwind "$attachments"}
                   {$match {$and (cond-> [{:attachments.latestVersion.contentType {$exists true}}]
                                   type-id
                                   (conj {:attachments.type.type-id type-id})

                                   (contains? fields :attachment.label.contents)
                                   (conj {:attachments.contents {$regex trim-text $options "i"}}))}}
                   {$skip (* limit page)}
                   {$limit (inc limit)}                     ;; Fetch 1 more than the limit to check if there's more
                   {$project {"attachments.latestVersion.contentType"                   1
                              "attachments.type"                                        1
                              "attachments.metadata"                                    1
                              "attachments.contents"                                    1
                              "attachments.id"                                          1
                              "attachments.op.id"                                       1
                              "address"                                                 1
                              "municipality"                                            1
                              "_applicantIndex"                                         1
                              "organization"                                            1
                              "buildings"                                               1
                              "attachments.state"                                       1
                              "modified"                                                1
                              "attachments.latestVersion.fileId"                        1
                              "attachments.latestVersion.filename"                      1
                              "attachments.latestVersion.created"                       1
                              "authority.firstName"                                     1
                              "authority.lastName"                                      1
                              "_designerIndex"                                          1
                              "propertyId"                                              1
                              "documents.data.kaytto.kayttotarkoitus.value"             1
                              "documents.data.valtakunnallinenNumero.value"             1
                              "documents.data.kunnanSisainenPysyvaRakennusnumero.value" 1
                              "documents.schema-info.op.id"                             1
                              "_projectDescriptionIndex"                                1
                              "primaryOperation"                                        1
                              "secondaryOperations"                                     1
                              "verdicts.paatokset.poytakirjat.paatospvm"                1
                              "verdicts.kuntalupatunnus"                                1
                              "location"                                                1
                              "_id"                                                     1}}
                   {$sort {:modified -1}}]
            results (->> (mc/aggregate db "applications" query :allow-disk-use true :cursor {:batch-size 100})
                         (map set-usage-type)
                         (map set-operation)
                         (map set-verdict-date)
                         (map set-buildings-by-operation)
                         (map set-kuntalupatunnukset)
                         (map #(assoc % :applicationId (:_id %) :suunnittelijat (:_designerIndex %) :grouping-key (:_id %) :applicants (:_applicantIndex %) :projectDescription (:_projectDescriptionIndex %)))
                         (map flatten-attachment-data)
                         (map #(dissoc % :documents :primaryOperation :secondaryOperations :_id :verdicts :_designerIndex :_applicantIndex :_projectDescriptionIndex)))
            _ (timbre/debug "***** Search query took " (- (System/currentTimeMillis) start) "ms")
            filtered-results (cond->> results
                                      usage (filter #((:kayttotarkoitukset %) usage))
                                      operation (filter #((:operations %) operation))
                                      (is-vtj-prt? text) (filter-by-national-id text))]
        (timbre/debug "Query: " query)
        #_(timbre/debug "Query explain:\n" (with-out-str (clojure.pprint/pprint (first (:stages (mc/explain-aggregate db "applications" query))))))
        {:has-more? (> (count results) limit)
         :took      (float (/ (- (System/currentTimeMillis) start) 1000))
         :results   (take limit filtered-results)}))))

(defn orgs-operations [db orgs]
  (->> (mc/find-maps db
                     "organizations"
                     {:_id {$in orgs}}
                     {:selected-operations 1})
       (map :selected-operations)
       (flatten)
       (set)))
