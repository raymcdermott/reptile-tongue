(ns reptile.tongue.main-view
  (:require
    [re-frame.core :refer [subscribe]]
    [reptile.tongue.events :as events]
    [reptile.tongue.views.login :as login]
    [reptile.tongue.views.editor :as editor]))

(defn main-panel
  []
  (let [logged-in-user        @(subscribe [::events/logged-in-user])
        network-users         @(subscribe [::events/network-repl-editors])
        visible-network-users @(subscribe [::events/visible-network-repl-editors])]
    (if-not logged-in-user
      [login/authenticate]
      [editor/main-panels logged-in-user network-users visible-network-users])))