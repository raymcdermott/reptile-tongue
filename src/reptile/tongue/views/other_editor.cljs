(ns reptile.tongue.views.other-editor
  (:require
    [re-frame.core :as re-frame]
    [re-com.core :refer [h-box v-box gap label md-icon-button]]
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
      (re-frame/dispatch [::events/set-other-editor-code-mirror code-mirror editor]))))

(defn other-component
  [editor]
  (reagent/create-class
    {:component-did-mount
     (other-editor-did-mount editor)

     :reagent-render
     (code-mirror/text-area editor)}))

(defn panel-controls
  [editor]
  [v-box :align :center :justify :center
   :children [[md-icon-button
               :tooltip (:name editor)
               :md-icon-name "zmdi-account-circle"
               :style (:style editor)
               :on-click #(re-frame/dispatch [::events/visibility-toggle
                                              (:editor editor)])]]])

(defn min-panel
  [editor]
  [h-box :align :center :justify :center :size "80px"
   :children
   [[label
     :label (:abbr editor)
     :style (:style editor)]
    [v-box :size "auto"
     :children
     [[panel-controls editor]]]
    [gap :size "30px"]]])

(defn other-panel
  [editor]
  [h-box :size "100px"
   :children
   [[panel-controls editor]
    [v-box :size "auto" :style eval-view/eval-panel-style
     :children [[other-component (:editor editor)]]]]])

(defn styled-editor
  [editor color]
  (let [editor-name (:name editor)]
    (merge editor {:editor editor-name
                   :abbr   (subs editor-name 0 (min (count editor) 2))
                   :style  {:color color}})))

; TODO - many colours
(defn styled-editors
  [editors]
  (let [editor-colors ["red" "blue" "green" "orange" "gray"]]
    (sort-by :name (map #(styled-editor %1 %2) editors editor-colors))))

(def other-editors-style {:padding "10px 0px 0px 20px"})

; TODO organise editors into a DB list and include the code-mirror
(defn other-editors
  [editors]
  (let [styled (styled-editors editors)]
    [h-box :height "25px" :style other-editors-style
     :children
     (vec (map #(min-panel %) styled))]))

(defn other-panels
  [editors]
  (let [styled          (styled-editors editors)
        visible-editors (sort-by :name (filter #(true? (:visibility %)) styled))]
    [v-box :size "auto"
     :children
     (vec (map #(other-panel %) visible-editors))]))
