(ns reptile.tongue.views.status
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                                 md-icon-button input-textarea modal-panel h-split v-split title flex-child-style
                                 radio-button p]]
            [reptile.tongue.subs :as subs]))

(def default-style {:font-family "Menlo, Lucida Console, Monaco, monospace"
                    :border      "1px solid lightgray"
                    :padding     "5px 5px 5px 5px"})

(def status-style (merge (dissoc default-style :border)
                         {:font-size   "10px"
                          :font-weight "lighter"
                          :color       "lightgrey"}))

(defn status-bar
  [user-name]
  (let [network-status @(re-frame/subscribe [::subs/network-status])
        network-style  {:color (if network-status "rgba(127, 191, 63, 0.32)" "red")}]
    [v-box :children
     [[line]
      [h-box :size "20px" :style status-style :gap "20px" :align :center :children
       [[label :label (str "Observer: " user-name)]
        [line]
        [label :style network-style :label "Connect Status:"]
        (if network-status
          [md-icon-button :md-icon-name "zmdi-cloud-done" :size :smaller :style network-style]
          [md-icon-button :md-icon-name "zmdi-cloud-off" :size :smaller :style network-style])]]]]))
