(ns reptile.tongue.views.editor
  (:require
    [re-frame.core :refer [subscribe dispatch]]
    [re-com.core :refer [h-box v-box box button gap line scroller border label input-text
                         md-circle-icon-button md-icon-button input-textarea h-split v-split
                         title flex-child-style p slider]]
    [re-com.splits :refer [hv-split-args-desc]]
    [reagent.core :as reagent]
    [reptile.tongue.code-mirror :as code-mirror]
    [reptile.tongue.events :as events]
    [reptile.tongue.subs :as subs]
    [reptile.tongue.views.other-editor :as other-editor]
    [reptile.tongue.views.add-lib :as add-lib]
    [reptile.tongue.views.eval :as eval-view]
    [reptile.tongue.views.visual-history :as visual-history]
    [reptile.tongue.views.status :as status]))

(defonce default-style {:font-family "Menlo, Lucida Console, Monaco, monospace"
                        :border      "1px solid lightgrey"
                        :padding     "5px 5px 5px 5px"})

(defonce eval-panel-style (merge (flex-child-style "1")
                                 default-style))

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
          extra-edit-keys {:Ctrl-Space code-mirror/trigger-autocomplete
                           :Cmd-Enter  (fn [cm]
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

(defn edit-panel
  [local-repl-editor]
  (let [current-form (subscribe [::subs/current-form])]
    (fn []
      [v-box :size "auto"
       :children
       [[box :size "auto" :style eval-panel-style :child
         [edit-component (:name local-repl-editor)]]
        [gap :size "5px"]
        [h-box :align :center
         :children
         [[button
           :label "Eval (or Cmd-Enter)" :tooltip "Send the form(s) for evaluation"
           :class "btn-success"
           :on-click #(dispatch [::events/eval @current-form])]
          [gap :size "20px"]]]]])))

(defn editors-panel
  [local-repl-editor network-repl-editors]
  [v-box :size "auto"
   :children
   [(when (not (empty? network-repl-editors))
      [other-editor/other-panels network-repl-editors])
    [edit-panel local-repl-editor]]])

(defn other-editor-row
  [network-repl-editors]
  [h-box :size "auto" :align :center
   :children
   (vec (map other-editor/min-panel network-repl-editors))])

(def doc-scroll-height "300px")

(defonce doc-style {:font-family "Menlo, Lucida Console, Monaco, monospace"
                    :position    "absolute"
                    :z-index     100})

(defonce doc-panel-style (merge (flex-child-style
                                  (str "0 0 " doc-scroll-height))
                                doc-style))

(defn doc-panel
  []
  (let [doc-text  (subscribe [::subs/doc-text])
        doc-show? (subscribe [::subs/doc-show?])]
    (fn []
      (when (and @doc-show? @doc-text)
        [h-box :width "100%"
         :children
         [[button :class "btn-warning btn-xs"
           :label "Clear docs"
           :on-click #(dispatch [::events/show-doc-panel false])]
          [gap :size "20px"]
          [scroller
           :height doc-scroll-height
           :child [p {:style {:color            "rgb(95, 95, 81)"
                              :background-color "rgba(255, 255, 255, 0.9)"}}
                   (:docs @doc-text)]]]]))))

(defn main-panels
  [user-name]
  (let [network-repl-editors (subscribe [::subs/network-repl-editors])
        local-repl-editor    (subscribe [::subs/local-repl-editor])]
    (fn []
      [v-box :style {:position "absolute"
                     :top      "5px"
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
        [h-split :splitter-size "2px"
         :panel-1 [editors-panel @local-repl-editor @network-repl-editors]
         :panel-2 [v-box :style eval-panel-style
                   :children
                   [[h-box :style doc-panel-style
                     :height doc-scroll-height :width "100%"
                     :children [[doc-panel]]]
                    [eval-view/eval-panel user-name]]]]
        [gap :size "10px"]
        [visual-history/history]
        [gap :size "10px"]
        [status/status-bar user-name]]])))
