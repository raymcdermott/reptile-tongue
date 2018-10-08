(ns reptile.tongue.main-view
  (:require
    [re-frame.core :refer [subscribe]]
    [reptile.tongue.subs :as subs]
    [reptile.tongue.views.login :as login]
    [reptile.tongue.views.observer :as observer]
    [reptile.tongue.views.editor :as editor]))

(defn main-panel
  []
  (let [observer?     (true? @(subscribe [::subs/observer]))
        user-name     @(subscribe [::subs/user-name])
        other-editors @(subscribe [::subs/other-editors user-name])]
    (if-not user-name
      [login/authenticate]
      (if observer?
        [observer/observer-panels user-name other-editors]
        [editor/main-panels user-name other-editors]))))