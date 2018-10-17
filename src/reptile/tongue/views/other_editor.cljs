(ns reptile.tongue.views.other-editor
  (:require
    [re-frame.core :as re-frame]
    [re-com.core :refer [h-box v-box gap label md-icon-button slider]]
    [re-com.splits :refer [hv-split-args-desc]]
    [reagent.core :as reagent]
    [reptile.tongue.subs :as subs]
    [reptile.tongue.events :as events]
    [reptile.tongue.code-mirror :as code-mirror]
    [reptile.tongue.views.eval :as eval-view]))

(def other-editors-style {:padding "20px 20px 20px 20px"})

(defn other-editor-did-mount
  [[editor-key _]]
  (fn [this]
    (let [node        (reagent/dom-node this)
          options     {:options {:lineWrapping true
                                 :readOnly     true}}
          code-mirror (code-mirror/parinfer node options)]
      (re-frame/dispatch [::events/network-repl-editor-code-mirror code-mirror editor-key]))))

(defn other-component
  [editor]
  (reagent/create-class
    {:component-did-mount
     (other-editor-did-mount editor)

     :reagent-render
     (code-mirror/text-area (first editor))}))

(defn editor-activity
  [[editor-key editor-properties]]
  (let [now                 (js/Date.now)
        last-active         (:last-active editor-properties)
        active              (:active editor-properties)
        inactivity-duration (quot (- now last-active) 1000)]
    [md-icon-button
     :tooltip (if active
                "Actively Coding"
                (if (> inactivity-duration 10)
                  (str "Last coding " inactivity-duration " seconds ago")
                  "Inactive"))
     :md-icon-name "zmdi-keyboard"
     :style (if active (:style editor-properties) {:color "lightgray"})
     :on-click #(re-frame/dispatch [::events/network-user-visibility-toggle editor-key])]))

(defn editor-icon
  [[editor-key editor-properties]]
  [md-icon-button
   :tooltip (:name editor-properties)
   :md-icon-name "zmdi-account-circle"
   :style (:style editor-properties)
   :on-click #(re-frame/dispatch [::events/network-user-visibility-toggle editor-key])])

(defn min-panel
  [editor]
  [h-box :align :center :justify :center :size "80px"
   :children
   [[editor-activity editor]
    [editor-icon editor]]])

(defn other-panel
  [editor]
  [h-box :size "auto"
   :children
   [[editor-icon editor]
    [v-box :size "auto" :style eval-view/eval-panel-style
     :children
     [[other-component editor]]]]])

(defn visibility-slider
  [editors]
  (let [visible-editor-count @(re-frame/subscribe [::subs/visible-editor-count])
        slider-val           (reagent/atom (str (or visible-editor-count
                                                    (count editors))))]
    (fn
      []
      [v-box
       :children
       [[label :label (str "Max Visible Editors " @slider-val)]
        [slider
         :model slider-val
         :max (str (count editors))
         :width "150px"
         :on-change (fn
                      [new-val]
                      (reset! slider-val (str new-val))
                      (re-frame/dispatch [::events/visible-editor-count new-val]))]]])))

(defn other-panels
  [visible-editors]
  [v-box :size "auto"
   :children
   (vec (map #(other-panel %) visible-editors))])