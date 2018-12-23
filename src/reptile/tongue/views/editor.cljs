(ns reptile.tongue.views.editor
  (:require
    [re-frame.core :refer [subscribe dispatch]]
    [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                         md-icon-button input-textarea h-split v-split popover-anchor-wrapper
                         popover-content-wrapper title flex-child-style p slider]]
    [re-com.splits :refer [hv-split-args-desc]]
    [reagent.core :as reagent]
    [reptile.tongue.code-mirror :as code-mirror]
    [reptile.tongue.events :as events]
    [reptile.tongue.subs :as subs]
    [reptile.tongue.views.other-editor :as other-editor]
    [reptile.tongue.views.add-lib :as add-lib]
    [reptile.tongue.views.eval :as eval-view]
    [reptile.tongue.views.status :as status]))

(defonce default-style {:font-family   "Menlo, Lucida Console, Monaco, monospace"
                        :border-radius "8px"
                        :border        "1px solid lightgrey"
                        :padding       "5px 5px 5px 5px"})

(defonce eval-panel-style (merge (flex-child-style "1")
                                 default-style))

(defonce edit-style (assoc default-style :border "1px solid lightgrey"))
(defonce edit-panel-style (merge (flex-child-style "1") edit-style))

(defonce history-button-style (assoc default-style
                                :padding "0px 0px 0px 0px"
                                :background-color "lightgrey"
                                :border "1px solid lightgrey"))

(defonce history-buttons-style (merge (flex-child-style "1")
                                      history-button-style))

(defonce status-style (merge (dissoc default-style :border)
                             {:font-size   "10px"
                              :font-weight "lighter"
                              :color       "lightgrey"}))

(defn notify-edits
  [new-value change-object]
  (dispatch [::events/current-form new-value change-object]))

(defn editor-did-mount
  []
  (fn [this-textarea]
    (let [node            (reagent/dom-node this-textarea)
          extra-edit-keys {:Cmd-Enter (fn [cm]
                                        (dispatch
                                          [::events/eval (.getValue cm)]))}
          options         {:options {:lineWrapping  true
                                     :autofocus     true
                                     :matchBrackets true
                                     :lineNumbers   true
                                     :extraKeys     extra-edit-keys}}
          code-mirror     (code-mirror/parinfer node options)]

      (.on code-mirror "change" (fn [cm co]
                                  (notify-edits (.getValue cm) co)))

      (dispatch [::events/repl-editor-code-mirror code-mirror]))))

(defn edit-component
  [panel-name]
  (reagent/create-class
    {:component-did-mount  (editor-did-mount)
     :reagent-render       (code-mirror/text-area panel-name)
     :component-did-update #(-> nil)                        ; noop to prevent reload
     :display-name         "local-editor"}))

;; need to coalesce next / previous code ...
;; way too much duplication
(defn history-previous-component
  [history-item]
  (let [{:keys [index]} history-item]
    [md-icon-button
     :tooltip "Previous item from history"
     :tooltip-position :above-center
     :md-icon-name "zmdi-chevron-left"
     :on-click #(dispatch [::events/from-history (dec index)])]))

(defn history-next-component
  [history-item]
  (let [{:keys [index]} history-item]
    [md-icon-button
     :tooltip "Next item from history"
     :tooltip-position :above-center
     :md-icon-name "zmdi-chevron-right"
     :on-click #(dispatch [::events/from-history (inc index)])]))

(defn completions-component
  []
  [label
   :style {:color "lightgray"}
   :label ""])

(defn edit-panel
  [local-repl-editor]
  (let [current-form (subscribe [::subs/current-form])
        history-item (subscribe [::subs/history-item])]
    (fn []
      (let [editor-name (:name local-repl-editor)]
        [v-box :size "auto"
         :children
         [[h-box :width "auto"
           :children
           [[box :justify :center
             :style history-buttons-style
             :child [history-previous-component @history-item]]
            [box :justify :center
             :style history-buttons-style
             :child [history-next-component @history-item]]]]
          [box :size "auto"
           :style edit-panel-style
           :child [edit-component editor-name]]
          [gap :size "5px"]
          [h-box
           :children
           [[button
             :label "Eval (or Cmd-Enter)"
             :tooltip "Send the form(s) for evaluation"
             :class "btn-success"
             :on-click #(dispatch [::events/eval @current-form])]
            [gap :size "5px"]
            ;; Only show if completions available
            (when (= 1 0)
              [box :size "auto" :style edit-panel-style
               :child [completions-component]])]]]]))))

(defn editors-panel
  [local-repl-editor network-repl-editors]
  (let [visible-count (some->> network-repl-editors
                               (filter (fn [[_ val]] (:visibility val)))
                               count)]
    [v-box :gap "5px" :size "auto"
     :children
     [(when (and visible-count (> visible-count 0))
        [other-editor/other-panels network-repl-editors])
      [edit-panel local-repl-editor (count network-repl-editors)]]]))

(defn other-editor-row
  [network-repl-editors]
  [h-box :size "auto" :align :center
   :children
   (vec (map other-editor/min-panel network-repl-editors))])

;; TODO rounded panel shapes
(defn main-panels
  [user-name]
  (let [network-repl-editors (subscribe [::subs/network-repl-editors])
        local-repl-editor    (subscribe [::subs/local-repl-editor])]
    (fn []
      [v-box :style {:position "absolute"
                     :top      "0px"
                     :bottom   "0px"
                     :width    "100%"}
       :children
       [[h-box :height "20px" :style other-editor/other-editors-style
         :children
         [[h-box :align :center :justify :start
           :children
           [[button :label "⏏️ Logout" :class "btn-default btn-sm"
             :tooltip "Logout of the system"
             :on-click #(dispatch [::events/logout])]]]
          [gap :size "10px"]
          [h-box :align :center :justify :start
           :children
           [[:img {:alt   "reptile"
                   :width "40px" :height "40px"
                   :src   "/images/reptile-logo-gray-transparent.png"}]]]
          [gap :size "10px"]
          [h-box :align :center
           :children
           [[add-lib/add-lib-panel]
            [button :label "🛠 Add Library" :class "btn-default btn-sm"
             :tooltip "Dynamically add a dependency"
             :on-click #(dispatch [::events/show-add-lib-panel true])]]]
          [gap :size "100px"]
          [h-box :align :center
           :children [[other-editor-row @network-repl-editors]]]]]
        [h-split :splitter-size "3px"
         :panel-1 [editors-panel @local-repl-editor @network-repl-editors]
         :panel-2 [v-box :style eval-panel-style
                   :children [[eval-view/eval-panel user-name]]]]
        [gap :size "10px"]
        [status/status-bar user-name]]])))
