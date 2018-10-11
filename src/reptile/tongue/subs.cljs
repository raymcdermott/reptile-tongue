(ns reptile.tongue.subs
  (:require
    [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::user-keystrokes
  (fn [db [_ user]]
    (get-in db [:current-forms user])))

(re-frame/reg-sub
  ::form-from-history
  (fn [db]
    (:form-from-history db)))

(re-frame/reg-sub
  ::eval-results
  (fn [db]
    (:eval-results db)))

(re-frame/reg-sub
  ::show-times
  (fn [db]
    (:show-times db)))

(re-frame/reg-sub
  ::network-status
  (fn [db]
    (:network-status db)))

(re-frame/reg-sub
  ::name
  (fn [db]
    (:name db)))

(re-frame/reg-sub
  ::current-form
  (fn [db]
    (:current-form db)))

(re-frame/reg-sub
  ::logged-in
  (fn [db]
    (:logged-in db)))

(re-frame/reg-sub
  ::user-name
  (fn [db]
    (:user-name db)))

(defn other-editors
  [editors current-editor]
  (filter #(not (= (:name %) current-editor)) editors))

(defn search-editors
  [editors current-editor]
  (filter #(not (= (:name %) current-editor)) editors))

(re-frame/reg-sub
  ::visible-editor-count
  (fn [db]
    (:visible-editor-count db)))

(re-frame/reg-sub
  ::visible-editors
  (fn [db [_ current-editor]]
    (let [editors       (:annotated-editors db)
          other-editors (other-editors editors current-editor)]
      (filter #(true? (:visibility %)) other-editors))))

(re-frame/reg-sub
  ::active-editors
  (fn [db [_ current-editor]]
    (let [editors       (:annotated-editors db)
          other-editors (other-editors editors current-editor)]
      (filter #(true? (:active %)) other-editors))))

(re-frame/reg-sub
  ::other-editors
  (fn [db [_ current-editor]]
    (let [editors (:annotated-editors db)]
      (other-editors editors current-editor))))

(re-frame/reg-sub
  ::observer
  (fn [db]
    (= "true" (:observer db))))

(re-frame/reg-sub
  ::observers
  (fn [db]
    (let [editors (:annotated-editors db)]
      (filter #(= "true" (:observer %)) editors))))

