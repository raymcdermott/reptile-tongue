(ns reptile.tongue.core
  (:require
    [reagent.core :as reagent]
    [re-frame.core :as re-frame]
    [reptile.tongue.auth0 :as auth0]
    [reptile.tongue.events :as events]
    [reptile.tongue.main-view :as main-view]
    [reptile.tongue.config :as config]))

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [main-view/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (re-frame/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (mount-root))
