(ns reptile.tongue.views.other-editor
  (:require
    [re-frame.core :as re-frame :refer [subscribe]]
    [re-com.core :refer [box h-box v-box gap label
                         md-icon-button slider flex-child-style]]
    [re-com.splits :refer [hv-split-args-desc]]
    [reagent.core :as reagent]
    [reptile.tongue.subs :as subs]
    [reptile.tongue.events :as events]
    [reptile.tongue.code-mirror :as code-mirror]
    [reptile.tongue.views.eval :as eval-view]))

(defonce other-editors-style {:padding "20px 20px 20px 10px"})

(defn other-editor-did-mount
  [editor]
  (fn [this]
    (let [node        (reagent/dom-node this)
          options     {:options {:lineWrapping true
                                 :readOnly     true}}
          code-mirror (code-mirror/parinfer node options)
          editor-key  (keyword (:name editor))]
      (re-frame/dispatch [::events/network-repl-editor-code-mirror code-mirror editor-key]))))

;; TODO visibility toggle ... we never get here cos react
(defn other-component
  [network-repl-editor]
  (let [editor-name (:name network-repl-editor)]
    (reagent/create-class
      {:component-did-mount  (other-editor-did-mount network-repl-editor)
       :reagent-render       (code-mirror/text-area editor-name)
       :component-did-update #(-> nil)                      ; noop to prevent reload
       :display-name         (str "network-editor-" editor-name)})))

; 1 - use the outer / inner pattern
; 2 - use the visible invisible property on each editor icon
; 3 - outer component to subscribe on the given editor and check the visibility

(defn editor-activity
  [editor-key network-repl-editor]
  (let [now                 (.now js/Date)
        last-active         (:last-active network-repl-editor)
        active              (:active network-repl-editor)
        inactivity-duration (- now last-active)]
    ;TODO: BUG editor is being counted as active when they are NOT typing
    ;(println :last-active last-active :inactivity-duration inactivity-duration :now now)
    [md-icon-button
     :tooltip "Click to show / hide"
     :md-icon-name (str "zmdi-eye" (if (:visibility network-repl-editor) "-off"))
     :size :smaller
     :style (if (> 30000 inactivity-duration) (:style network-repl-editor) {:color "lightgray"})
     :on-click #(re-frame/dispatch [::events/network-user-visibility-toggle editor-key])]))

(defn editor-icon
  [editor-key network-repl-editor]
  [md-icon-button
   :tooltip (:name network-repl-editor)
   :md-icon-name (:icon network-repl-editor)
   :style (:style network-repl-editor)
   :on-click #(re-frame/dispatch [::events/network-user-visibility-toggle editor-key])])

(defn min-panel
  [[editor-key network-repl-editor]]
  (when editor-key
    [h-box :align :center :justify :center :size "80px"
     :children
     [[editor-activity editor-key network-repl-editor]
      [editor-icon editor-key network-repl-editor]]]))

(defonce other-panel-style (merge (flex-child-style "1")
                                 {:font-family "Menlo, Lucida Console, Monaco, monospace"
                                  :border      "1px solid lightgrey"
                                  :padding "5px 5px 5px 5px"}))

; TODO: BUG re-display the most recent form when the component is made visible
; use the inner / outer pattern from re-frame
(defn network-editor-panel
  [[editor-key network-repl-editor]]
  (when (and editor-key (true? (:visibility network-repl-editor)))
    [box :size "auto" :style other-panel-style
     :child [other-component network-repl-editor]]))

(defn other-panels
  [network-repl-editors]
  [v-box :size "auto"
   :children
   (vec (map #(network-editor-panel %) network-repl-editors))])
