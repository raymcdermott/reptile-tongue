(ns repl-ui.events
  (:require [re-frame.core :as re-frame]
            [repl-ui.db :as db]))

(re-frame/reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

(re-frame/reg-event-fx
  ::form-to-eval
  ;; integrate par-edit here?
  (fn [{:keys [db]} [_ item]]
    {:db (assoc db :eval-item item)}))


