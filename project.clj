(defproject lupadoku "2023.1"
  :description "Lupapiste document search application"
  :url "https://www.lupapiste.fi"
  :license {:name         "European Union Public Licence v. 1.2"
            :url          "https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12"
            :distribution :manual}
  :repositories [["osgeo" {:url "https://repo.osgeo.org/repository/release/"}]]
  :dependencies [;; Clojure
                 [org.clojure/clojure "1.10.3"]
                 [http-kit "2.5.3"]
                 [ring/ring-core "1.9.4"]
                 [compojure "1.6.2"]
                 [com.stuartsierra/component "1.0.0"]
                 [com.taoensso/timbre "5.1.2"]
                 [viesti/timbre-json-appender "0.2.5"]
                 [org.slf4j/slf4j-api "1.7.32"]
                 [org.slf4j/log4j-over-slf4j "1.7.32" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.32" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jcl-over-slf4j "1.7.32" :exclusions [org.slf4j/slf4j-api]]
                 [ch.qos.logback/logback-classic "1.2.9" :exclusions [org.slf4j/slf4j-api]]
                 ;; 3.x should be compatible with monger, 3.12 is also tested as being MongoDB 5.0 compatible
                 [org.mongodb/mongodb-driver "3.12.10"]
                 [com.novemberain/monger "3.5.0" :exclusions [com.google.guava/guava]]
                 [com.cemerick/url "0.1.1"]
                 [clj-time "0.15.2"]
                 [metosin/muuntaja "0.6.8"]

                 ;; Lupapiste
                 [lupapiste/commons "3.1.10"]
                 [lupapiste/document-search-commons "1.0.5"]

                 ;; ClojureScript
                 [org.clojure/clojurescript "1.10.879"]]

  :main ^:skip-aot lupadoku.app
  :target-path "target/%s/"
  :profiles {:uberjar {:aot :all
                       :prep-tasks ^:replace ["clean"
                                              ["cljsbuild" "once" "prod"]
                                              "javac"
                                              "compile"]}
             :dev {:source-paths ["dev"]
                   :dependencies [[reloaded.repl "0.2.4"]]
                   :sass {:source-paths ["checkouts/document-search-commons/scss/"]
                          :target-path  "checkouts/document-search-commons/resources/public/css"
                          :output-style :expanded
                          :source-map   true}
                   :plugins [[lein-cljsbuild "1.1.8"]
                             [deraen/lein-sass4clj "0.3.1"]
                             [lein-figwheel "0.5.20" :exclusions [org.clojure/clojure
                                                                  org.clojure/clojure org.codehaus.plexus/plexus-utils]]]}}
  :uberjar-name "lupadoku.jar"
  :source-paths ["src/clj" "src/cljc"]
  :clean-targets ^{:protect false} ["resources/public/main.js"
                                    "resources/public/out"
                                    :target-path]
  :cljsbuild {:builds {:dev {:source-paths ["src/cljs" "src/cljc" "checkouts/document-search-commons/src/cljs" "checkouts/document-search-commons/src/cljc"]
                             :compiler {:main lupadoku.ui.app
                                        :output-to "resources/public/main.js"
                                        :asset-path "/document-search/out"}
                             :figwheel { :on-jsload "lupadoku.ui.app/start"}}
                       :prod {:source-paths ["src/cljs" "src/cljc"]
                              :compiler {:main lupadoku.ui.app
                                         :output-to "resources/public/main.js"
                                         :output-dir "resources/public/out"
                                         :source-map "resources/public/main.js.map"
                                         :language-in  :ecmascript6
                                         :rewrite-polyfills true
                                         :language-out :ecmascript5
                                         :optimizations :advanced
                                         :closure-extra-annotations ["api" "observable"]}}}}
  :figwheel {:server-port 3450
             :css-dirs ["checkouts/document-search-commons/resources/public/css"]}
  :repl-options {:init-ns user}
  :manifest {:build-info {"git-commit" ~(fn [_] (.trim (:out (clojure.java.shell/sh "git" "rev-parse" "--verify" "HEAD"))))
                          "build" ~(fn [_] (or (System/getenv "BUILD_TAG") "unknown"))}}
  :aliases {"extract-strings" ["run" "-m" "lupapiste-commons.i18n.extract/extract-strings" "t"]})
