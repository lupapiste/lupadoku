(ns lupadoku.ui.components.search-results
  (:require [search-commons.utils.i18n :refer [t]]
            [search-commons.utils.state :as state]
            [search-commons.components.multi-select-view :as ms]
            [reagent.core :as reagent]
            [goog.string.format]
            [search-commons.routing :as routing]
            [clojure.string :as s]
            [search-commons.components.search-results :as sr])
  (:import [goog.i18n DateTimeFormat]))


(defn translate-metadata-value [k v]
  (if (contains? #{:pituus} k)
    (.replace (t :sailytysaika.pituus) "{0}" v)
    (t v)))

(def relevant-metadata-keys [:julkisuusluokka :nakyvyys :tila])

(defn gen-metadata-hiccup [metadata]
  (doall
    (for [mk relevant-metadata-keys]
      (let [mv (mk metadata)]
        (when-not (s/blank? mv)
          [:div.metadata-item {:key mk}
           (if (map? mv)
             [:div.metadata-submap
              [:div.metadata-submap-header (t mk)]
              [:div.metadata-value (gen-metadata-hiccup mv)]]
             [:div
              [:div.metadata-header (t mk)]
              [:div.metadata-value (translate-metadata-value mk mv)]])])))))


(defonce document-in-view (reagent/atom []))

(def result-view
  (with-meta
    (fn []
      (if @state/multi-select-mode
        (ms/select-view)
        (when-let [result @state/selected-result]
          (let [{:keys [applicationId propertyId address applicants municipality operations source-system tiedostonimi
                        kayttotarkoitukset organization type metadata contents fileId filename contentType
                        authority suunnittelijat buildingIds id kuntalupatunnukset projectDescription arkistoija
                        nationalBuildingIds] {:keys [myyntipalvelu]} :metadata} result]
            [:div.result-view
             [:div.result-view-header
              [:h4 (t (sr/type-str type))]
              [:div.document-contents contents]
              [:div
               (when-not (s/blank? address)
                 [:span address ", "])
               (sr/municipality-name municipality) " - " (sr/to-human-readable-property-id propertyId) " - " (s/join ", " applicants)]
              (when applicationId
                [:div
                 [:a.application-link {:href (str "/app/fi/authority#!/application/" applicationId)}
                  applicationId]])]
             [:div.result-view-content
              [:div.preview
               [:div.preview-image {:on-click (fn [] (when-not (= contentType "image/tiff")
                                                       (reset! document-in-view {:id            id
                                                                                 :organization  organization
                                                                                 :file-id       fileId
                                                                                 :filename      (or filename tiedostonimi)
                                                                                 :source-system source-system
                                                                                 :content-type  contentType})))}
                [:div
                 (let [url (if (= :onkalo source-system)
                             (routing/path (str "/onkalo-preview/" organization "/" id))
                             (str "/api/raw/latest-attachment-version?preview=true&attachment-id=" id))]
                   [:img {:src url}])]
                (when-not (= contentType "image/tiff")
                  [:div
                   [:span.btn.primary.view-document
                    [:i.lupicon-eye]
                    [:span (t "Avaa dokumentti")]]])]
               (when fileId
                 [:div.result-button
                  [:a.btn.secondary.download-document {:href (str "/api/raw/latest-attachment-version?download=true&attachment-id=" id)}
                   [:i.lupicon-download]
                   [:span (t "Lataa dokumentti")]]])
               (when (or (= "arkistoitu" (:tila metadata)) (= :onkalo source-system))
                 [:div.result-button
                  [:a.btn.secondary.download-document {:href (routing/path (str "/onkalo-download/" organization "/" id "?download=true"))}
                   [:i.lupicon-download]
                   [:span (t "Lataa arkistokappale")]]])]
              [:div.metadata
               (when (seq operations)
                 [:div.metadata-item
                  [:div.metadata-header (t "Toimenpide")]
                  (doall (for [op-name operations]
                           ^{:key op-name}
                           [:div.metadata-value (t op-name)]))])
               (when (seq kayttotarkoitukset)
                 [:div.metadata-item
                  [:div.metadata-header (t "Käyttötarkoitus")]
                  (doall (for [usage-type kayttotarkoitukset]
                           ^{:key usage-type}
                           [:div.metadata-value (t usage-type)]))])
               (when (seq kuntalupatunnukset)
                 [:div.metadata-item
                  [:div.metadata-header (t "Kuntalupatunnus")]
                  (doall (for [tunnus kuntalupatunnukset]
                           ^{:key tunnus}
                           [:div.metadata-value tunnus]))])
               (when-not (s/blank? projectDescription)
                 [:div.metadata-item
                  [:div.metadata-header (t "projectDescription")]
                  [:div.metadata-long-value projectDescription]])
               (when-not (s/blank? (:lastName authority))
                 [:div.metadata-item
                  [:div.metadata-header (t :handler)]
                  [:div.metadata-value (str (:firstName authority) " " (:lastName authority))]])
               (when (seq suunnittelijat)
                 [:div.metadata-item
                  [:div.metadata-header (t "Hankkeen suunnittelijat")]
                  [:div.metadata-value (s/join ", " suunnittelijat)]])
               (when (seq nationalBuildingIds)
                 [:div.metadata-item
                  [:div.metadata-header (t "Valtakunnalliset rakennustunnukset")]
                  (doall
                    (for [bid nationalBuildingIds]
                      ^{:key (str id "-" bid)}
                      [:div.metadata-value bid]))])
               (when (seq buildingIds)
                 [:div.metadata-item
                  [:div.metadata-header (t "Kunnan rakennustunnukset")]
                  (doall
                    (for [bid buildingIds]
                      ^{:key (str id "-" bid)}
                      [:div.metadata-value bid]))])
               (gen-metadata-hiccup metadata)
               (when-not (s/blank? (:lastName arkistoija))
                 [:div.metadata-item
                  [:div.metadata-header (t "arkistoija")]
                  [:div.metdadata-value (str (:firstName arkistoija) " " (:lastName arkistoija))]])
               (when (or (true? myyntipalvelu) (false? myyntipalvelu))
                 [:div.metadata-item
                  [:div.metadata-header (t "myyntipalvelu")]
                  [:div.metadata-value (if myyntipalvelu (t "yes") (t "no"))]])
               [:div.metadata-item
                [:div.metadata-header (t "Arkistointitunnus")]
                [:div.metadata-value id]]]]]))))
    {:component-did-update sr/animate-view-transition}))

(defn document-view []
  (let [{:keys [id organization filename content-type source-system]} @document-in-view]
    (when id
      [:div.document-view
       [:div.document-view-header
        filename
        [:div.document-view-exit {:on-click #(reset! document-in-view {})}
         [:i.icon-cancel-circled]]]
       [:div.iframe-container
        (let [element (if (s/starts-with? content-type "image/") :img :iframe)
              url (if (= :onkalo source-system)
                    (routing/path (str "/onkalo-download/" organization "/" id))
                    (str "/api/raw/latest-attachment-version?attachment-id=" id))]
          (if (s/ends-with? content-type "xml")
            (do (sr/fetch-div-content id url)
                [:div {:class "xml-container" :dangerouslySetInnerHTML {:__html (:text @sr/div-content)}}])
            [element {:src url}]))]])))
