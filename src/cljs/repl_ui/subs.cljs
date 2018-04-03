(ns repl-ui.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::other-editors
  (fn [db [_ me]]
    (let [editors (map name (keys (:editors db)))]
      (remove #{me} (set editors)))))

(re-frame/reg-sub
  ::user-name
  (fn [db]
    (get-in db [:user-name :user])))

(re-frame/reg-sub
  ::eval-results
  (fn [db]
    (:eval-results db)))

(re-frame/reg-sub
  ::status
  (fn [db]
    (:status db)))

(re-frame/reg-sub
  ::key-down
  (fn [db]
    ; do the mapping here (91 = Cmd-, 17 Ctrl-, 18 Alt-)
    (:key-down db)))

(re-frame/reg-sub
  ::name
  (fn [db]
    (:name db)))

(re-frame/reg-sub
  ::edit-keystrokes
  ;; integrate par-edit here for the editor?
  (fn [db]
    (:current-form-edits db)))

(re-frame/reg-sub
  ::parinfer-form
  ;; integrate parinfer here for the editor?
  (fn [db]
    (:parinfer-form db)))

(re-frame/reg-sub
  ::user-keystrokes
  ;; integrate par-edit here for other users?
  (fn [db [_ user]]
    (get-in db [:current-forms user])))



