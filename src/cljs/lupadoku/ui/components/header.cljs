(ns lupadoku.ui.components.header
  (:require [search-commons.utils.i18n :refer [t]]
            [search-commons.routing :as routing]
            [search-commons.utils.state :as state]
            [clojure.string :as string]
            [reagent.core :as reagent])
  (:require-macros [search-commons.utils.macros :refer [handler-fn]]))

(defn display-name [user]
  (let [{:keys [firstName lastName]} user]
    (when firstName
      (str firstName " " lastName))))

(defn language-options [language-open?]
  [:ul
   (doall
     (for [lang [:fi :sv :en]]
       ^{:key lang} [:li
                     [:a {:href "#" :on-click (handler-fn (state/set-lang! lang)
                                                          (reset! language-open? false)) }
                      (str (string/upper-case (name lang)) " - " (t lang))]]))])

(defn app-link [lang path]
  (str "/app/" lang "/" path))

(defn header []
  (let [language-open? (reagent/atom false)]
    (fn []
      (let [{:keys [user]} @state/config
            user-name (display-name user)
            lang (name (-> @state/translations :current-lang))]
        [:nav.nav-wrapper
         [:div.nav-top
          [:div.nav-box
           [:div.brand
            [:a.logo {:href (app-link lang "authority#!/applications")
                      :style {:background (str "url(" (routing/path "/img/lupapiste-logo.svg") ")")
                              :background-size "164px 35px"}}
             ""]]
           [:div#language-select {:class (if @language-open? "language-open" "language-closed")}
            [:a {:href "#" :on-click (handler-fn (swap! language-open? not))}
             [:span lang]
             [:span {:class (if @language-open? "lupicon-chevron-small-up" "lupicon-chevron-small-down")}]]]
           [:div.header-menu
            [:div.header-box
             [:a {:href (app-link lang "authority#!/applications") :title (t "navigation.dashboard")}
              [:span.header-icon.lupicon-documents]
              [:span.narrow-hide (t "navigation.dashboard")]]]
            [:div.header-box
             [:a {:href "/document-search" :title (t "Dokumentit")}
              [:span.header-icon.lupicon-archives]
              [:span.narrow-hide (t "Dokumentit")]]]
            [:div.header-box
             [:a {:href (str "/tiedonohjaus?" lang) :title (t "Tiedonohjaus")}
              [:span.header-icon.lupicon-tree-path]
              [:span.narrow-hide (t "Tiedonohjaus")]]]
            [:div.header-box
             [:a {:href (t "path.guide") :target "_blank" :title (t "help")}
              [:span.header-icon.lupicon-circle-question]
              [:span.narrow-hide (t "help")]]]
            [:div.header-box
             [:a {:href (app-link lang "#!/mypage")
                  :title (t "mypage.title")}
              [:span.header-icon.lupicon-user]
              [:span.narrow-hide (or user-name (t "Ei käyttäjää"))]]]
            [:div.header-box
             [:a {:href (app-link lang "logout") :title (t "logout")}
              [:span.header-icon.lupicon-log-out]
              [:span.narrow-hide (t "logout")]]]]]]
         (when @language-open?
           [:div.nav-bottom
            [:div.nav-box
             [:div.language-menu
              (language-options language-open?)]]])]))))
