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

(defn editor-activity
  [editor]
  [md-icon-button
   :tooltip (:name editor)
   :md-icon-name "zmdi-keyboard"
   :style (if (:active editor) (:style editor) {:color "lightsgray"})
   :on-click #(re-frame/dispatch [::events/visibility-toggle
                                  (:editor editor)])])

(defn editor-icon
  [editor]
  [md-icon-button
   :tooltip (:name editor)
   :md-icon-name "zmdi-account-circle"
   :style (:style editor)
   :on-click #(re-frame/dispatch [::events/visibility-toggle
                                  (:editor editor)])])

(defn min-panel
  [editor]
  [h-box :align :center :justify :center :size "80px"
   :children
   [[editor-activity editor]
    [editor-icon editor]]])

(defn other-panel
  [editor]
  [h-box :size "100px"
   :children
   [[editor-icon editor]
    [v-box :size "auto" :style eval-view/eval-panel-style
     :children
     [[other-component (:editor editor)]]]]])

(def other-editors-style {:padding "10px 0px 0px 20px"})

(defn other-editors
  [editors]
  [h-box :height "25px" :style other-editors-style
   :children
   (vec (map #(min-panel %) (sort-by :name editors)))])

(defn other-panels
  [editors]
  (let [visible-editors (sort-by :name (filter #(true? (:visibility %))
                                               editors))]
    [v-box :size "auto"
     :children
     (vec (map #(other-panel %) visible-editors))]))