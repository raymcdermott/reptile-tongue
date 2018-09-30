(ns reptile.tongue.views.eval
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                                 md-icon-button input-textarea modal-panel h-split v-split title flex-child-style
                                 radio-button p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [reagent.core :as reagent]
            [reptile.tongue.events :as events]
            [reptile.tongue.code-mirror :as code-mirror]))

(def default-style {:font-family "Menlo, Lucida Console, Monaco, monospace"
                    :border      "1px solid lightgray"
                    :padding     "5px 5px 5px 5px"})

(def eval-panel-style (merge (flex-child-style "1")
                             default-style))

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
    {:component-did-mount
     (eval-did-mount)

     :reagent-render
     (code-mirror/text-area (str "eval-" panel-name))}))

(defn eval-panel
  [panel-name]
  [v-box :size "auto" :style eval-panel-style
   :children [[h-box :align :center :justify :end :gap "20px" :size "40px"
               :children [[md-icon-button
                           :tooltip "Show evaluation times (TBD)"
                           :md-icon-name "zmdi-timer"]
                          [md-icon-button
                           :tooltip "Clear evaluations (TBD)"
                           :md-icon-name "zmdi-delete"]
                          [md-icon-button
                           :tooltip "Wrap text (default ON)"
                           :md-icon-name "zmdi-wrap-text"]]]
              [line]
              [box :size "auto"
               :child [eval-component panel-name]]]])
