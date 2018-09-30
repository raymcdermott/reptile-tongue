(ns reptile.tongue.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [reptile.tongue.events :as events]
            [reptile.tongue.main-view :as main-view]))


(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [main-view/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (re-frame/dispatch-sync [::events/initialize-db])
  (mount-root))
