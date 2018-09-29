(ns reptile.tongue.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                                 md-icon-button input-textarea modal-panel h-split v-split title flex-child-style
                                 radio-button p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [reptile.tongue.events :as events]
            [reptile.tongue.subs :as subs]
            [reptile.tongue.observer :as observer]
            [reagent.core :as reagent]
            [cljsjs.codemirror]
            [cljsjs.codemirror.mode.clojure]
            [cljsjs.codemirror.addon.edit.matchbrackets]
            [cljsjs.parinfer-codemirror]
            [cljsjs.parinfer]))

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

;; CodeMirror support

(defn code-mirror-text-area
  [id]
  (fn [] [:textarea {:id            id
                     :auto-complete false
                     :default-value ""}]))

(defn code-mirror-parinfer
  [dom-node config]
  (let [editor-options (clj->js (merge {:mode :clojure} (:options config)))
        code-mirror    (js/CodeMirror.fromTextArea dom-node editor-options)
        editor-height  (get-in config [:size :height] "100%")
        editor-width   (get-in config [:size :width] "100%")]
    (.setSize code-mirror editor-height editor-width)
    (js/parinferCodeMirror.init code-mirror)
    code-mirror))

;; Components

(defn other-editor-did-mount
  [editor]
  (fn [this]
    (let [node        (reagent/dom-node this)
          options     {:options {:lineWrapping true
                                 :readOnly true}}
          code-mirror (code-mirror-parinfer node options)]
      (re-frame/dispatch [::events/other-editors-code-mirrors code-mirror editor]))))

(defn other-editor-component
  [editor]
  (reagent/create-class
    {:component-did-mount
     (other-editor-did-mount editor)

     :reagent-render
     (code-mirror-text-area editor)}))

(defn other-editor-panel
  [editor]
  [v-box :size "auto" :style eval-panel-style
   :children [[label :label editor]
              [other-editor-component editor]]])



(defn eval-did-mount
  []
  (fn [this]
    (letfn [(scrollToBottom [cm]
              (.scrollIntoView cm #js {:line (.lastLine cm)}))]
      (let [node        (reagent/dom-node this)
            options     {:options {:lineWrapping true
                                   :readOnly     true}}
            code-mirror (code-mirror-parinfer node options)]
        (.on code-mirror "change" #(scrollToBottom code-mirror))
        (re-frame/dispatch [::events/eval-code-mirror code-mirror])))))

(defn eval-component
  [panel-name]
  (reagent/create-class
    {:component-did-mount
     (eval-did-mount)

     :reagent-render
     (code-mirror-text-area (str "eval-" panel-name))}))

(defn eval-panel
  [panel-name]
  [v-box :size "auto" :style eval-panel-style
   :children [[eval-component panel-name]]])

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

; TODO - add :classifier, :extension, :exclusions options
(defn maven-dep
  [lib-data]
  [v-box :gap "10px" :children
   [[label :label "Maven Version"]
    [input-text
     :width "350px"
     :model (:version @lib-data)
     :on-change #(swap! lib-data assoc :version %)]]])

; TODO - add :tag option
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
  "Place wrapping quotes around raw strings to save them from getting lost in transit"
  (let [clean-form   (clojure.string/trim new-value)
        current-form (if (and (= (first clean-form) \") (= (last clean-form) \"))
                       (pr-str new-value)
                       new-value)]
    (re-frame/dispatch [::events/current-form new-value])))

(defn editor-did-mount
  []
  (fn [this]
    (let [node            (reagent/dom-node this)
          extra-edit-keys {:Cmd-Enter #(re-frame/dispatch
                                         [::events/eval (.getValue %)])}
          options         {:options {:lineWrapping true
                                     :autofocus     true
                                     :matchBrackets true
                                     :lineNumbers   true
                                     :extraKeys     extra-edit-keys}}
          code-mirror     (code-mirror-parinfer node options)]
      (.on code-mirror "change" #(notify-edits (.getValue %)))
      (re-frame/dispatch [::events/editor-code-mirror code-mirror]))))

(defn edit-component
  [panel-name]
  (reagent/create-class
    {:component-did-mount
     (editor-did-mount)

     :reagent-render
     (code-mirror-text-area panel-name)}))

; TODO: Refactor out the add-lib modal
(defn edit-panel
  [panel-name]
  (let [show-add-lib? (reagent/atom false)
        lib-data      (reagent/atom {:name    "clojurewerkz/money"
                                     :version "1.10.0"
                                     ; TODO: use a real SHA
                                     :url     "https://github.com/iguana"
                                     :sha     "888abcd888888b5cba88882b8888bdf59f9d88b6"
                                     :maven   true})
        add-lib-event (fn []
                        (reset! show-add-lib? false)
                        (re-frame/dispatch [::events/add-lib @lib-data]))]
    (fn []
      (println "Edit mode " panel-name)
      (let [current-form @(re-frame/subscribe [::subs/current-form])]
        [v-box :size "auto"
         :children
         [[box :size "auto" :style eval-panel-style :child
           [edit-component panel-name]]
          [gap :size "5px"]
          [h-box :align :center
           :children
           [[button
             :label "Eval (or Cmd-Enter)"
             :on-click #(re-frame/dispatch [::events/eval current-form])]
            [gap :size "30px"]
            [md-circle-icon-button
             :md-icon-name "zmdi-plus"
             :tooltip "Add a library"
             :size :smaller
             :on-click #(reset! show-add-lib? true)]
            (when @show-add-lib?
              [modal-panel
               :backdrop-color "lightgray"
               :backdrop-on-click #(reset! show-add-lib? false)
               :backdrop-opacity 0.7
               :child [add-lib-form lib-data add-lib-event]])
            [gap :size "20px"]
            [visual-history]]]]]))))

(defn status-bar
  [user-name]
  (let [network-status @(re-frame/subscribe [::subs/network-status])
        network-style  {:color (if network-status "rgba(127, 191, 63, 0.32)" "red")}]
    [v-box :children
     [[line]
      [h-box :size "20px" :style status-style :gap "20px" :align :center :children
       [[label :label (str "Editor: " user-name)]
        [line]
        [label :style network-style :label "Connect Status:"]
        (if network-status
          [md-icon-button :md-icon-name "zmdi-cloud-done" :size :smaller :style network-style]
          [md-icon-button :md-icon-name "zmdi-cloud-off" :size :smaller :style network-style])]]]]))

(defn login-form
  [form-data process-ok]
  [border
   :border "1px solid #eee"
   :child [v-box
           :size "auto"
           :gap "30px" :padding "10px"
           :children [[title :label "Welcome to REPtiLe" :level :level2]
                      [v-box
                       :gap "10px"
                       :children [[label :label "User name"]
                                  [input-text
                                   :model (:user @form-data)
                                   :on-change #(swap! form-data assoc :user %)]
                                  [label :label "Shared secret"]
                                  [input-text
                                   :model (:secret @form-data)
                                   :on-change #(swap! form-data assoc :secret %)]
                                  [label :label "Observer mode?"]
                                  [v-box
                                   :children
                                   [(doall (for [o ["true" "false"]]
                                             ^{:key o}
                                             [radio-button
                                              :label o
                                              :value o
                                              :model (:observer @form-data)
                                              :on-change #(swap! form-data assoc :observer %)]))]]
                                  [gap :size "30px"]
                                  [button :label "Access" :on-click process-ok]]]]]])

(defn other-editor-panels
  [other-editors]
  (when other-editors
    [v-box :size "auto"
     :children (vec (map #(other-editor-panel %) other-editors))]))

(defn main-panels
  [user-name other-editors]
  [v-box :style {:position "absolute"
                 :top      "18px"
                 :bottom   "0px"
                 :width    "100%"}
   :children
   [[h-split :splitter-size "2px" :initial-split "45%"
     :panel-1 (if (empty? other-editors)
                [edit-panel user-name]
                [v-split :initial-split "30%"
                 :panel-1 [other-editor-panels other-editors]
                 :panel-2 [edit-panel user-name]])
     :panel-2 [eval-panel user-name]]
    [gap :size "10px"]
    [status-bar user-name]]])

; TODO - have a different key for observers rather selecting it on a form
(defn login
  []
  (let [logged-in  @(re-frame/subscribe [::subs/logged-in])
        form-data  (reagent/atom {:user     "your-name"
                                  :secret   "warm-blooded-lizards-rock"
                                  :observer "false"})
        process-ok (fn [] (re-frame/dispatch [::events/login @form-data]))]
    (fn []
      (when-not logged-in
        [modal-panel
         :backdrop-color "lightblue"
         :backdrop-opacity 0.1
         :child [login-form form-data process-ok]]))))

(defn main-panel
  []
  (let [observer?     (true? @(re-frame/subscribe [::subs/observer]))
        user-name     @(re-frame/subscribe [::subs/user-name])
        other-editors @(re-frame/subscribe [::subs/other-editors user-name])]
    (if user-name
      (if observer?
        [observer/observer-panels user-name other-editors]
        [main-panels user-name other-editors])
      [login])))


;; TODO - enable keymap support for VIM / EMACS
;; which are available from CodeMirror
