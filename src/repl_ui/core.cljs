(ns repl-ui.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [repl-ui.events :as events]
            [repl-ui.views :as views]))


(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  ;   (dispatch-sync [::events/boot])

  (re-frame/dispatch-sync [::events/initialize-db])
  (mount-root))
