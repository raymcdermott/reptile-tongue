(ns reptile.tongue.views.other-editor
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                                 md-icon-button input-textarea modal-panel h-split v-split title flex-child-style
                                 radio-button p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [reagent.core :as reagent]
            [reptile.tongue.events :as events]
            [reptile.tongue.code-mirror :as code-mirror]
            [reptile.tongue.views.eval :as eval-view]))

(defn other-editor-did-mount
  [editor]
  (fn [this]
    (let [node        (reagent/dom-node this)
          options     {:options {:lineWrapping true
                                 :readOnly     true}}
          code-mirror (code-mirror/parinfer node options)]
      (re-frame/dispatch [::events/other-editors-code-mirrors code-mirror editor]))))

(defn other-component
  [editor]
  (reagent/create-class
    {:component-did-mount
     (other-editor-did-mount editor)

     :reagent-render
     (code-mirror/text-area editor)}))

(defn other-panel
  [editor]
  [v-box :size "auto" :style eval-view/eval-panel-style
   :children [[label :label editor]
              [other-component editor]]])

(defn other-panels
  [other-editors]
  (if other-editors
    [v-box :size "auto"
     :children (vec (map #(other-panel %) other-editors))]
    [v-box :size "auto"
     :children [[line]
                [label :label "Waiting for editors to join"]
                [line]]]))
