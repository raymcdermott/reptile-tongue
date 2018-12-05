(ns reptile.tongue.views.editor
  (:require
    [re-frame.core :as re-frame :refer [subscribe dispatch]]
    [re-com.core :refer [h-box v-box box button gap line scroller border label
                         input-text md-circle-icon-button md-icon-button
                         input-textarea modal-panel h-split v-split title
                         flex-child-style radio-button p slider]]
    [re-com.splits :refer [hv-split-args-desc]]
    [reagent.core :as reagent]
    [reptile.tongue.code-mirror :as code-mirror]
    [reptile.tongue.events :as events]
    [reptile.tongue.subs :as subs]
    [reptile.tongue.views.other-editor :as other-editor]
    [reptile.tongue.views.eval :as eval-view]
    [reptile.tongue.views.visual-history :as visual-history]
    [reptile.tongue.views.status :as status]
    [clojure.string :as str]))

(defonce default-style {:font-family "Menlo, Lucida Console, Monaco, monospace"
                        :border      "1px solid lightgrey"
                        :padding     "5px 5px 5px 5px"})

(defonce eval-panel-style (merge (flex-child-style "1")
                                 default-style))

(defonce status-style (merge (dissoc default-style :border)
                             {:font-size   "10px"
                              :font-weight "lighter"
                              :color       "lightgrey"}))

(defn lib-type
  [lib-data]
  [v-box :gap "20px"
   :children [(doall (for [maven? [:maven :git]]            ;; Notice the ugly "doall"
                       ^{:key maven?}                       ;; key should be unique among siblings
                       [radio-button
                        :label (name maven?)
                        :value maven?
                        :model (if (:maven @lib-data) :maven :git)
                        :on-change #(swap! lib-data assoc :maven (= :maven %))]))]])

(defn dep-name
  [lib-data]
  [v-box :gap "10px" :children
   [[label :label "Dependency Name"]
    [input-text
     :width "350px"
     :model (:name @lib-data)
     :on-change #(swap! lib-data assoc :name %)]]])

(defn maven-dep
  [lib-data]
  [v-box :gap "10px" :children
   [[label :label "Maven Version"]
    [input-text
     :width "350px"
     :model (:version @lib-data)
     :on-change #(swap! lib-data assoc :version %)]]])

(defn git-dep
  [lib-data]
  [v-box :gap "10px" :children
   [[label :label "Repository URL"]
    [input-text
     :width "350px"
     :model (:url @lib-data)
     :on-change #(swap! lib-data assoc :url %)]
    [label :label "Commit SHA"]
    [input-text
     :width "350px"
     :model (:sha @lib-data)
     :on-change #(swap! lib-data assoc :sha %)]]])

(defn add-lib-form
  [lib-data process-ok]
  (fn []
    [border
     :border "1px solid #eee"
     :child [v-box
             :gap "30px" :padding "10px"
             :height "450px"
             :children
             [[title :label "Add a dependency to the REPL" :level :level2]
              [v-box
               :gap "10px"
               :children [[lib-type lib-data]
                          [dep-name lib-data]               ; cond
                          (if (:maven @lib-data)
                            [maven-dep lib-data]
                            [git-dep lib-data])
                          [gap :size "30px"]
                          [button :label "Add" :on-click process-ok]]]]]]))


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

; TODO: Refactor out the add-lib modal
(defn edit-panel
  [local-repl-editor]
  (let [show-add-lib? (reagent/atom false)
        lib-data      (reagent/atom {:name    "org.clojure/test.check"
                                     :version "0.10.0-alpha3"
                                     :url     "https://github.com/clojure/test.check.git"
                                     :sha     "8bc8057d7954674673ae0329b3233139ddba3f71"
                                     :maven   true})
        add-lib-event (fn
                        []
                        (reset! show-add-lib? false)
                        (dispatch [::events/add-lib @lib-data]))
        current-form  (subscribe [::subs/current-form])]
    (fn
      []
      [v-box :size "auto"
       :children
       [[box :size "auto" :style eval-panel-style :child
         [edit-component (:name local-repl-editor)]]
        [gap :size "5px"]
        [h-box :align :center
         :children
         [[button
           :label "Eval (or Cmd-Enter)"
           :on-click #(dispatch [::events/eval @current-form])]
          [gap :size "150px"]
          [md-icon-button
           :md-icon-name "zmdi-library"
           :tooltip "Add a library"
           :on-click #(reset! show-add-lib? true)]
          (when @show-add-lib?
            [modal-panel
             :backdrop-color "lightgray"
             :backdrop-on-click #(reset! show-add-lib? false)
             :backdrop-opacity 0.7
             :child [add-lib-form lib-data add-lib-event]])]]]])))

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

(defn main-panels
  [user-name]
  (let [network-repl-editors (subscribe [::subs/network-repl-editors])
        local-repl-editor    (subscribe [::subs/local-repl-editor])]
    (fn
      []
      [v-box :style {:position "absolute"
                     :top      "10px"
                     :bottom   "0px"
                     :width    "100%"}
       :children
       [[h-box :height "25px" :style other-editor/other-editors-style
         :children
         [[h-box :align :center
           :children [[button :label "Logout"
                       :on-click #(dispatch [::events/logout])]]]
          [gap :size "100px"]
          [h-box :align :center
           :children [[other-editor-row @network-repl-editors]]]]]
        [h-split :splitter-size "2px"
         :panel-1 [editors-panel @local-repl-editor @network-repl-editors]
         :panel-2 [eval-view/eval-panel user-name]]
        [gap :size "10px"]
        [visual-history/history]
        [gap :size "10px"]
        [status/status-bar user-name]]])))
