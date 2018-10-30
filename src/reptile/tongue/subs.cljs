(ns reptile.tongue.subs
  (:require
    [re-frame.core :refer [reg-sub]]))

(reg-sub
  ::user-keystrokes
  (fn [db [_ user]]
    (get-in db [:current-forms user])))

(reg-sub
  ::form-from-history
  (fn [db]
    (:form-from-history db)))

(reg-sub
  ::eval-results
  (fn [db]
    (:eval-results db)))

(reg-sub
  ::show-times
  (fn [db]
    (:show-times db)))

(reg-sub
  ::network-status
  (fn [db]
    (:network-status db)))

(reg-sub
  ::name
  (fn [db]
    (:name db)))

(reg-sub
  ::current-form
  (fn [db]
    (:current-form db)))

(reg-sub
  ::local-repl-editor
  (fn [db]
    (:local-repl-editor db)))

(reg-sub
  ::logged-in
  (fn [db]
    (:logged-in db)))

(reg-sub
  ::observers
  (fn [db]
    (let [editors (:annotated-editors db)]
      (filter #(= "true" (:observer %)) editors))))

(reg-sub
  ::logged-in-user
  (fn [db]
    (:user db)))

(reg-sub
  ::user-code-mirror
  (fn [db]
    (get-in db [:local-repl-editor :code-mirror])))

(reg-sub
  ::network-repl-editor
  (fn [db [_ editor-key]]
    (get-in db [:network-repl-editors editor-key])))

(reg-sub
  ::network-repl-editors
  (fn [db]
    (:network-repl-editors db)))

(reg-sub
  ::network-repl-editor-keys
  (fn [db]
    (keys (:network-repl-editors db))))


