(ns repl-ui.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::eval-items
 (fn [db]
   (:eval-item db)))

(re-frame/reg-sub
  ::name
  (fn [db]
    (:name db)))