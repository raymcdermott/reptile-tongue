(ns reptile.tongue.views.editor
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                                 md-icon-button input-textarea modal-panel h-split v-split title flex-child-style
                                 radio-button p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [reagent.core :as reagent]
            [reptile.tongue.code-mirror :as code-mirror]
            [reptile.tongue.events :as events]
            [reptile.tongue.subs :as subs]
            [reptile.tongue.views.other-editor :as other-editor]
            [reptile.tongue.views.eval :as eval-view]
            [reptile.tongue.views.visual-history :as visual-history]
            [reptile.tongue.views.status :as status]))

(def default-style {:font-family "Menlo, Lucida Console, Monaco, monospace"
                    :border      "1px solid lightgray"
                    :padding     "5px 5px 5px 5px"})

(def eval-panel-style (merge (flex-child-style "1")
                             default-style))

(def history-style {:padding "5px 5px 0px 10px"})

(def status-style (merge (dissoc default-style :border)
                         {:font-size   "10px"
                          :font-weight "lighter"
                          :color       "lightgrey"}))

(defn format-history-item
      [historical-form]
      [md-icon-button
       :md-icon-name "zmdi-comment-text"
       :tooltip historical-form
       :on-click #(re-frame/dispatch [::events/from-history historical-form])])

(defn visual-history
      []
      (fn []
          (let [eval-results @(re-frame/subscribe [::subs/eval-results])]
               (when eval-results
                     [h-box :size "auto" :align :center :style history-style
                      :children (map format-history-item
                                     (distinct (map :form eval-results)))]))))

(defn lib-type
      [lib-data]
      [v-box :gap "20px"
       :children [(doall (for [maven? [:maven :git]]        ;; Notice the ugly "doall"
                              ^{:key maven?}                ;; key should be unique among siblings
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
                                [dep-name lib-data]         ; cond
                                (if (:maven @lib-data)
                                  [maven-dep lib-data]
                                  [git-dep lib-data])
                                [gap :size "30px"]
                                [button :label "Add" :on-click process-ok]]]]]]))

(defn notify-edits
      [new-value]
      (re-frame/dispatch [::events/current-form new-value]))

(defn editor-did-mount
      []
      (fn [this]
          (let [node (reagent/dom-node this)
                extra-edit-keys {:Cmd-Enter #(re-frame/dispatch
                                               [::events/eval (.getValue %)])}
                options {:options {:lineWrapping  true
                                   :autofocus     true
                                   :matchBrackets true
                                   :lineNumbers   true
                                   :extraKeys     extra-edit-keys}}
                code-mirror (code-mirror/parinfer node options)]
               (.on code-mirror "change" #(notify-edits (.getValue %)))
               (re-frame/dispatch [::events/editor-code-mirror code-mirror]))))

(defn edit-component
      [panel-name]
      (reagent/create-class
        {:component-did-mount
         (editor-did-mount)

         :reagent-render
         (code-mirror/text-area panel-name)}))

; TODO: Refactor out the add-lib modal
(defn edit-panel
      [panel-name]
      (let [show-add-lib? (reagent/atom false)
            lib-data (reagent/atom {:name    "clojurewerkz/money"
                                    :version "1.10.0"
                                    :url     "https://github.com/?????"
                                    :sha     "666-???"
                                    :maven   true})
            add-lib-event (fn []
                              (reset! show-add-lib? false)
                              (re-frame/dispatch [::events/add-lib @lib-data]))]
           (fn []
               (let [current-form @(re-frame/subscribe [::subs/current-form])]
                    [v-box :size "auto"
                     :children
                     [[box :size "auto" :style eval-panel-style :child
                       [edit-component panel-name]]
                      [gap :size "5px"]
                      [h-box :align :center
                       :children
                       [[button
                         :label "Eval (or Cmd-LL)"
                         :on-click #(re-frame/dispatch [::events/eval current-form])]
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
                               :child [add-lib-form lib-data add-lib-event]])]]]]))))

(defn main-panels
      [user-name annotated-editors]
      [v-box :style {:position "absolute"
                     :top      "0px"
                     :bottom   "0px"
                     :width    "100%"}
       :children
       [[other-editor/other-editors annotated-editors]
        [h-split :splitter-size "2px" :initial-split "45%"
         :panel-1 (if (empty? annotated-editors)
                    [edit-panel user-name]
                    [v-split :splitter-size "2px" :initial-split "25%"
                     :panel-1 [other-editor/other-panels annotated-editors]
                     :panel-2 [edit-panel user-name]])
         :panel-2 [eval-view/eval-panel user-name]]
        [gap :size "10px"]
        [visual-history/history]
        [gap :size "10px"]
        [status/status-bar user-name]]])