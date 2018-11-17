(ns reptile.tongue.views.editor
  (:require
    [re-frame.core :as re-frame :refer [subscribe]]
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
  [new-value]
  (re-frame/dispatch [::events/current-form new-value]))

;; placeholder that we will track more completely later
(def clj-fns
  (sort (map (comp name first)
             (ns-publics 'cljs.core))))

(def word-re #"[\w$]+")

(defn completion-list
  [word]
  (let [return-list
        (apply array (cond->>
                       clj-fns word (filter (fn [s]
                                              (clojure.string/starts-with?
                                                s word)))))]
    (println return-list)
    return-list))

(defn clojure-completions
  [editor _]
  (let [cur      (.getCursor editor)
        line-num (.-line cur)
        end      (.-ch cur)
        cur-line (.getLine editor line-num)
        start    (loop [start end]
                   (if (.test word-re (.charAt cur-line (dec start)))
                     (recur (dec start))
                     start))
        cur-word (if (not= start end)
                   (subs cur-line start end))]

    (clj->js
      {:list (vec (cond->> clj-fns
                           cur-word (filter
                                      (fn [s]
                                        (clojure.string/starts-with?
                                          s cur-word)))))
       :from (js/CodeMirror.Pos line-num start)
       :to   (js/CodeMirror.Pos line-num end)})))

(defn trigger-autocomplete [cm]
  (js/setTimeout
    (fn []
      (when-not (.. cm -state -completionActive)
        (.showHint cm #js {:completeSingle false})))
    100)
  js/CodeMirror.Pass)

(defn editor-did-mount
  []
  (fn [this-textarea]
    (let [node            (reagent/dom-node this-textarea)
          extra-edit-keys {:Ctrl-Space trigger-autocomplete
                           :Cmd-Enter  (fn [cm]
                                         (re-frame/dispatch
                                           [::events/eval (.getValue cm)]))}
          options         {:options {:lineWrapping  true
                                     :autofocus     true
                                     :matchBrackets true
                                     :lineNumbers   true
                                     :hintOptions   {:hint clojure-completions}
                                     :extraKeys     extra-edit-keys}}
          code-mirror     (code-mirror/parinfer node options)]

      (.on code-mirror "change" (fn [cm _] (notify-edits (.getValue cm))))

      (re-frame/dispatch [::events/repl-editor-code-mirror code-mirror]))))

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
        lib-data      (reagent/atom {:name    "clojurewerkz/money"
                                     :version "1.10.0"
                                     :url     "https://github.com/?????"
                                     :sha     "666-???"
                                     :maven   true})
        add-lib-event (fn
                        []
                        (reset! show-add-lib? false)
                        (re-frame/dispatch [::events/add-lib @lib-data]))
        current-form  (re-frame/subscribe [::subs/current-form])
        completions   (re-frame/subscribe [::subs/completions])]
    (fn
      []
      [v-box :size "auto"
       :children
       [[box :size "auto" :style eval-panel-style :child
         [edit-component (:name local-repl-editor)]]
        [gap :size "5px"]
        [h-box
         :children
         [[label :label "Completions"]
          (when (not-empty @completions)
            (doall (map (fn [c] [label :label c])
                        (take 5 @completions))))]]
        [gap :size "5px"]
        [h-box :align :center
         :children
         [[button
           :label "Eval (or Cmd-Enter)"
           :on-click #(re-frame/dispatch [::events/eval @current-form])]
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
                       :on-click #(re-frame/dispatch [::events/logout])]]]
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
