(defproject reptile/tongue "0.1.0-SNAPSHOT"

  :min-lein-version "2.5.3"

  :global-vars {*warn-on-reflection* true
                *assert*             true}

  :dependencies [[org.clojure/clojure "1.10.0-alpha4"]
                 [org.clojure/clojurescript "1.10.238"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/tools.logging "0.4.0"]
                 [figwheel-sidecar "0.5.5"]
                 [com.taoensso/sente "1.12.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.cognitect/transit-cljs "0.8.243"]
                 [binaryage/oops "0.5.8"]
                 [parinfer-cljs "1.5.1-0"]
                 [cljsjs/rangy-core "1.3.0-1"]
                 [cljsjs/rangy-textrange "1.3.0-1"]
                 [reagent "0.7.0"]
                 [re-frame "0.10.5"]
                 [re-com "2.1.0"]
                 [day8.re-frame/async-flow-fx "0.0.8"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]]

  :source-paths ["src"]

  :plugins [[lein-pprint "1.2.0"]
            [lein-ancient "0.6.14"]
            [lein-cljsbuild "1.1.7"]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.4"]]
                   :plugins      [[lein-figwheel "0.5.13"]]}}

  :cljsbuild {:builds [{:id           "dev"
                        :source-paths ["src"]
                        :figwheel     {:on-jsload "reptile.tongue.core/mount-root"}
                        :compiler     {:main                 reptile.tongue.core
                                       :output-to            "resources/public/js/compiled/app.js"
                                       :output-dir           "resources/public/js/compiled/out"
                                       :asset-path           "js/compiled/out"
                                       :source-map-timestamp true
                                       :preloads             [devtools.preload]
                                       :external-config      {:devtools/config {:features-to-install :all}}}}

                       {:id           "min"
                        :source-paths ["src"]
                        :compiler     {:main            reptile.tongue.core
                                       :output-to       "resources/public/js/compiled/app.js"
                                       :optimizations   :advanced
                                       :closure-defines {goog.DEBUG false}
                                       :pretty-print    false}}]})
