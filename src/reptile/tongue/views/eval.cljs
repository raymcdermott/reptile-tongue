(ns reptile.tongue.views.eval
  (:require
    [re-frame.core :as re-frame]
    [re-com.core :refer [h-box v-box box button gap line border label input-text
                         md-circle-icon-button md-icon-button input-textarea
                         popover-anchor-wrapper popover-content-wrapper modal-panel
                         h-split v-split title flex-child-style radio-button p]]
    [reagent.core :as reagent]
    [reptile.tongue.subs :as subs]
    [reptile.tongue.events :as events]
    [reptile.tongue.code-mirror :as code-mirror]))

; padding order is: top right bottom left
(defonce eval-panel-style (merge (flex-child-style "1")
                                 {:padding "5px 5px 5px 5px"}))

(defonce eval-component-style (merge (flex-child-style "1")
                                     {:padding "5px 5px 0px 5px"}))

(defn eval-did-mount
  []
  (fn [this]
    (letfn [(scrollToBottom [cm]
              (.scrollIntoView cm #js {:line (.lastLine cm)}))]
      (let [node        (reagent/dom-node this)
            options     {:options {:lineWrapping true
                                   :readOnly     true}}
            code-mirror (code-mirror/parinfer node options)]
        (.on code-mirror "change" #(scrollToBottom code-mirror))
        (re-frame/dispatch [::events/eval-code-mirror code-mirror])))))

(defn eval-component
  [panel-name]
  (reagent/create-class
    {:component-did-mount  (eval-did-mount)
     :reagent-render       (code-mirror/text-area (str "eval-" panel-name))
     :component-did-update #(-> nil)                        ; noop to prevent reload
     :display-name         "eval"}))

(defn eval-panel
  [panel-name]
  (let [show-times? (reagent/atom false)]
    [v-box :size "auto" :style eval-panel-style
     :children
     [[h-box :align :center :justify :end :gap "20px" :height "20px"
       :children
       [[md-icon-button
         :tooltip "Show evaluation times"
         :size :smaller
         :md-icon-name "zmdi-timer"
         :style {:color (if @show-times? "red" "black")}
         :on-click #(do (reset! show-times? (false? @show-times?))
                        (re-frame/dispatch [::events/show-times @show-times?]))]
        [md-icon-button
         :tooltip "Clear evaluations"
         :size :smaller
         :md-icon-name "zmdi-delete"
         :on-click #(re-frame/dispatch [::events/clear-evals])]
        [md-icon-button
         :tooltip "Wrap text (default ON)"
         :size :smaller
         :md-icon-name "zmdi-wrap-text"]]]
      [box :size "auto" :style eval-component-style
       :child [eval-component panel-name]]]]))
