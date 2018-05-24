(ns reptile.tongue.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::other-editors
  (fn [db [_ me]]
    (let [editors (map name (keys (:editors db)))]
      (remove #{me} (set editors)))))

(re-frame/reg-sub
  ::user-name
  (fn [db]
    (:user-name db)))

(re-frame/reg-sub
  ::eval-results
  (fn [db]
    (:eval-results db)))

(re-frame/reg-sub
  ::status
  (fn [db]
    (:status db)))

(re-frame/reg-sub
  ::network-status
  (fn [db]
    (:network-status db)))

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
  ::parinfer-cursor
  (fn [db]
    (let [{:keys [cursor-x cursor-line text]} (:parinfer-result db)]
      {:cursor-x    cursor-x
       :cursor-line cursor-line
       :text        text})))

(re-frame/reg-sub
  ::user-keystrokes
  ;; integrate par-edit here for other users?
  (fn [db [_ user]]
    (get-in db [:current-forms user])))

(re-frame/reg-sub
  ::logged-in
  (fn [db]
    (:logged-in db)))

