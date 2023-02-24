(ns lupadoku.ui.app
  (:require [cemerick.url :as url]
            [cljs.pprint :refer [pprint]]
            [lupadoku.routing]
            [lupadoku.ui.components.footer :as footer]
            [lupadoku.ui.components.header :as header]
            [lupadoku.ui.components.search-results :as sr]
            [reagent.dom :as rd]
            [search-commons.components.search-form :as search-form]
            [search-commons.components.search-results :as common-search-results]
            [search-commons.routing :as routing]
            [search-commons.utils.i18n :refer [t]]
            [search-commons.utils.state :as state]
            [search-commons.utils.utils :as utils]))

(defn search-view []
  [:div.app-container
   [header/header]
   [:div.wrapper {:id "wrapper"}
    [:h1 (t "Dokumenttiarkisto")]
    [search-form/input-form]
    [common-search-results/result-section sr/document-view sr/result-view]]
   [footer/footer]])

(defn ^:export start []
  (utils/setup-print-to-console!)
  (routing/set-root lupadoku.routing/root)
  (state/fetch-operations)
  (state/fetch-translations :fi)
  (state/fetch-user-and-config)
  (when-let [lang (state/selected-language (:query (url/url (-> js/window .-location .-href))))]
    (state/set-lang! lang))
  (rd/render [search-view] (.getElementById js/document "app")))
