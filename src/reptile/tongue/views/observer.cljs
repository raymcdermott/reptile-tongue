(ns reptile.tongue.views.observer
  (:require
    [re-com.core :refer [v-box h-split gap]]
    [reptile.tongue.views.eval :as eval-view]
    [reptile.tongue.views.other-editor :as other-editor]
    [reptile.tongue.views.status :as status]))

(defn observer-panels
  [user-name editors]
  [v-box :style {:position "absolute"
                 :top      "18px"
                 :bottom   "0px"
                 :width    "100%"}
   :children
   [[h-split :splitter-size "2px" :initial-split "45%"
     :panel-1 [other-editor/other-panels editors]
     :panel-2 [eval-view/eval-panel "Observer"]]
    [gap :size "10px"]
    [status/status-bar user-name]]])
