(defproject pollylog "0.1.6"
  :description "fixed format logbook for pollyxt lidars"
  :url "http://polly.tropos.de"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [ring-cors "0.1.12"]
                 [ring/ring-json "0.5.0"]
                 [http-kit "2.5.3"]
                 [compojure "1.6.1"]
                 [cider/cider-nrepl "0.17.0"] ; maybe necessary to show hints
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/data.csv "1.0.0"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.xerial/sqlite-jdbc "3.36.0.1"]
                 ;[clj-toml "0.3.1"]
                 ;frontend stuff
                 [org.clojure/clojurescript "1.11.4"]
                 [reagent "1.1.0"]
                 [antizer "0.3.1"] ; has to be the old version, a newer messes up some buttons
                 [funcool/cuerdas "2.2.1"]                 
                 [cljs-http/cljs-http "0.1.46"]
                 [cljs-ajax "0.8.4"]]
  :main ^:skip-aot pollylog.core
  :target-path "target/%s"

  :source-paths ["src-backend"]
  ; section for cljs
  ; used the gadfly template https://github.com/gadfly361/reagent-figwheel   
  :min-lein-version "2.5.3"
  :plugins [[lein-cljsbuild "1.1.4"]]
  :clean-targets ^{:protect false} ["resources/public/js/"
                                    "target"]
  :figwheel {:css-dirs ["resources/public/css"]
             :server-port 3450
             :nrepl-port 7888}

  :profiles {:uberjar {:aot :all :auto-clean true 
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]}
              :dev
              {:dependencies [[figwheel-sidecar "0.5.16"]
                              [cider/piggieback "0.3.1"]
                              [org.clojure/tools.nrepl "0.2.10"]]
               :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
               :plugins      [[lein-figwheel "0.5.16"]]}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src-frontend/cljs"]
     :figwheel     {:on-jsload "pollylogbrowser.core/reload"}
     :compiler     {:main                 pollylogbrowser.core
                    :optimizations        :none
                    :output-to            "resources/public/js/app.js"
                    :output-dir           "resources/public/js/dev"
                    :asset-path           "js/dev"
                    :source-map-timestamp true}}

    {:id           "min"
     :source-paths ["src-frontend/cljs"]
     :compiler     {:main            pollylogbrowser.core
                    :optimizations   :advanced
                    :output-to       "resources/public/js/app.js"
                    :output-dir      "resources/public/js/min"
                    :elide-asserts   true
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}]})


