(ns reptile.tongue.views.visual-history
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                                 md-icon-button input-textarea modal-panel h-split v-split title flex-child-style
                                 radio-button p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [reptile.tongue.events :as events]
            [reptile.tongue.subs :as subs]))

(def history-style {:padding "5px 5px 0px 10px"})

(defn format-history-item
  [historical-form]
  [md-icon-button
   :md-icon-name "zmdi-comment-text"
   :tooltip historical-form
   :on-click #(re-frame/dispatch [::events/from-history historical-form])])

(defn history
  []
  (fn []
    (let [eval-results @(re-frame/subscribe [::subs/eval-results])]
      (when eval-results
        [h-box :size "20px" :align :center :style history-style
         :children (map format-history-item
                        (distinct (map :form eval-results)))]))))
