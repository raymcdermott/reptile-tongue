(ns reptile.tongue.views.observer
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                                 md-icon-button input-textarea modal-panel h-split v-split title flex-child-style
                                 radio-button p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [reptile.tongue.views.eval :as eval-view]
            [reptile.tongue.views.other-editor :as other-editor]
            [reptile.tongue.views.status :as status]))

(defn observer-panels
  [user-name other-editors]
  (println "Observer mode " user-name)
  [v-box :style {:position "absolute"
                 :top      "18px"
                 :bottom   "0px"
                 :width    "100%"}
   :children
   [[h-split :splitter-size "2px" :initial-split "45%"
     :panel-1 [other-editor/other-panels other-editors]
     :panel-2 [eval-view/eval-panel "Guest"]]
    [gap :size "10px"]
    [status/status-bar user-name]]])
