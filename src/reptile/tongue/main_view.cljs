(ns reptile.tongue.main-view
  (:require
    [re-frame.core :refer [subscribe]]
    [reptile.tongue.subs :as subs]
    [reptile.tongue.views.login :as login]
    [reptile.tongue.views.editor :as editor]))

(defn main-panel
  []
  (let [logged-in-user @(subscribe [::subs/logged-in-user])]
    (if-not logged-in-user
      [login/authenticate]
      [editor/main-panels logged-in-user])))
