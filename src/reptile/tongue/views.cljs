(ns reptile.tongue.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                                 md-icon-button input-textarea modal-panel h-split v-split title flex-child-style
                                 radio-button p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [reptile.tongue.events :as events]
            [reptile.tongue.subs :as subs]
            [cljs.reader :as edn]
            [reagent.core :as reagent]
            [cljsjs.parinfer-codemirror]
            [cljsjs.parinfer]
            [clojure.string :as str]
            [cljs.reader :as rdr]
            [cljs.tools.reader.reader-types :as treader-types]))

(def default-style {:font-family "Menlo, Lucida Console, Monaco, monospace"
                    :border      "1px solid lightgray"
                    :padding     "5px 5px 5px 5px"})

(def readonly-panel-style (merge (flex-child-style "1")
                                 default-style
                                 {:background-color "rgba(171, 171, 129, 0.1)"
                                  :color            "gray"
                                  :resize           "none"}))

(def other-editor-style (merge (flex-child-style "1")
                               default-style
                               {:font-size        "10px"
                                :color            "gray"
                                :font-weight      "lighter"
                                :padding          "3px 3px 3px 3px"
                                :background-color "rgba(171, 171, 129, 0.1)"}))

(def input-style (merge (flex-child-style "1")
                        default-style
                        {:font-weight "bolder"
                         :resize      "none"}))

(def eval-panel-style (merge (flex-child-style "1")
                             default-style))

(def eval-content-style (merge (flex-child-style "1")
                               {:resize    "none"
                                :flex-flow "column-reverse nowrap"
                                :padding   "3px 3px 3px 3px"}))

(def history-style {:padding "5px 5px 0px 10px"})

(def status-style (merge (dissoc default-style :border)
                         {:font-size   "10px"
                          :font-weight "lighter"
                          :color       "lightgrey"}))

;; CodeMirror support

(defn code-mirror-parinfer
  [dom-node config]
  (let [editor-options (clj->js (merge {:mode "clojure"} (:options config)))
        code-mirror    (js/CodeMirror.fromTextArea dom-node editor-options)
        editor-height  (get-in config [:size :height] "100%")
        editor-width   (get-in config [:size :width] "100%")]
    (.setSize code-mirror editor-height editor-width)
    (js/parinferCodeMirror.init code-mirror)
    code-mirror))

(defn other-editor-did-mount
  [editor]
  (fn [this]
    (let [code-mirror (code-mirror-parinfer
                        (reagent/dom-node this)
                        {:options {:lineNumbers true
                                   :readOnly    true}})]
      (re-frame/dispatch [::events/other-editors-code-mirrors code-mirror editor]))))

(defn other-editor-component
  [editor]
  (reagent/create-class
    {:component-did-mount
     (other-editor-did-mount editor)

     :reagent-render
     (fn [] [:textarea {:id            editor
                        :auto-complete false
                        :default-value ""}])}))


(defn read-input-panel
  [user-name]
  (println "read-input-panel user-name" user-name)
  [v-box :size "auto" :style eval-panel-style
   :children [[label :label user-name]
              [other-editor-component user-name]]])

(defn format-trace
  [via trace]
  (let [show-trace? (reagent/atom false)]
    (fn
      []
      [v-box :children
       [[md-icon-button :md-icon-name "zmdi-zoom-in" :tooltip (first via) :on-click #(reset! show-trace? true)]
        (when @show-trace?
          [modal-panel :backdrop-on-click #(reset! show-trace? false)
           :child
           [scroller :height "85vh"
            :child
            [v-box :size "auto"
             :children
             [[label :label (:type (first via))]
              (map (fn [element] [label :label (str element)]) trace)
              [gap :size "20px"]
              [button :label "Done (or click the backdrop)" :on-click #(reset! show-trace? false)]]]]])]])))

(defn format-exception
  [val form]
  (let [{:keys [cause via trace]} (edn/read-string val)]
    (println "cause" cause "via" (:type (first via)))
    [v-box :size "auto"
     :children [[label :label form]
                [h-box :size "auto"
                 :children [[label :style {:color "red"} :label (str "=> " (or cause (:type (first via)) "??"))]
                            [gap :size "20px"]
                            [format-trace via trace]]]
                [gap :size "20px"]]]))


(defn format-result
  [result]
  (let [eval-result  (:prepl-response result)
        input-form   (:form (last eval-result))
        source       (:source result)
        returned-val (:val (last eval-result))
        val          (try (edn/read-string returned-val)
                          (catch js/Error e e))]
    ;(when-let [spec-fail-data (:data (last eval-result))]
    ;  (println "spec-fail:" spec-fail-data))
    (if (and (map? val) (= #{:cause :via :trace} (set (keys val))))
      [format-exception returned-val input-form]
      [v-box :size "auto" :children
       [[h-box :align :center :gap "20px"
         :children [[label :label input-form]
                    (when (= source :system)
                      [label :style {:color :lightgray :font-weight :lighter}
                       :label (str "(invoked by " (name source) ")")])]]
        (map (fn [printable]
               [label :label (:val printable)])
             (drop-last eval-result))
        [label :label (str "=> " returned-val)]             ; Properly format the output
        [gap :size "20px"]]])))

(defn scrollToBottom
  [code-mirror]
  (let [line-count (.lastLine code-mirror)]
    (.scrollIntoView code-mirror #js {:line line-count})))

(defn eval-did-mount []
  (fn [this]
    (let [node        (reagent/dom-node this)
          options     {:options {:lineNumbers  true
                                 :lineWrapping true
                                 :readOnly     true}}
          code-mirror (code-mirror-parinfer node options)]
      (.on code-mirror "change" #(scrollToBottom code-mirror))
      (re-frame/dispatch [::events/eval-code-mirror code-mirror]))))

(defn eval-component
  [panel-name]
  (reagent/create-class
    {:component-did-mount
     (eval-did-mount)

     :reagent-render
     (fn []
       [:textarea {:id            panel-name
                   :auto-complete false
                   :default-value ""}])}))

(defn eval-panel
  "We need to use a lower level reagent function to have the content scroll from the bottom"
  []
  (reagent/create-class
    {:component-did-update
     (fn [this]
       (let [node (reagent/dom-node this)]
         (set! (.-scrollTop node) (.-scrollHeight node))))

     :reagent-render
     (fn []
       (let [eval-results @(re-frame/subscribe [::subs/eval-results])]
         [scroller :style eval-panel-style
          :child
          [box :style eval-content-style :size "auto"
           :child
           [:div (when eval-results
                   [v-box :size "auto" :children
                    (map format-result eval-results)])]]]))}))

(defn format-history-item
  [historical-form]
  [md-icon-button
   :md-icon-name "zmdi-comment-text"
   :tooltip historical-form
   :on-click #(re-frame/dispatch [::events/from-history historical-form])])

(defn visual-history
  []
  (fn
    []
    (let [eval-results @(re-frame/subscribe [::subs/eval-results])]
      (when eval-results
        [h-box :size "auto" :align :center :style history-style :children
         (reverse (map format-history-item (distinct (map :form eval-results))))]))))


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


(defn code-mirror-editor-changes
  [change]
  (re-frame/dispatch [::events/current-form change]))

(defn editor-did-mount []
  (fn [this]
    (let [code-mirror (code-mirror-parinfer
                        (reagent/dom-node this)
                        {:options {:autofocus   true
                                   :lineNumbers true}})]
      (.on code-mirror "change" #(code-mirror-editor-changes (.getValue %)))
      (re-frame/dispatch [::events/editor-code-mirror code-mirror]))))

(defn edit-component
  [panel-name]
  (reagent/create-class
    {:component-did-mount
     (editor-did-mount)

     :reagent-render
     (fn [] [:textarea {:id            panel-name
                        :auto-complete false
                        :default-value ""}])}))

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
    (fn
      []
      (let [current-form @(re-frame/subscribe [::subs/current-form])]
        [v-box :size "auto"
         :children
         ;; TODO make edit-panel-style
         [[box :size "auto" :style eval-panel-style :child
           [edit-component panel-name]]
          [gap :size "5px"]
          [h-box :align :center
           :children
           [[button
             :label "Eval (or Alt-Enter)"
             :on-click #(re-frame/dispatch [::events/eval current-form])]
            [gap :size "30px"]
            [md-circle-icon-button :md-icon-name "zmdi-plus" :tooltip "Add a library" :size :smaller
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
  (let [edit-status    @(re-frame/subscribe [::subs/status])
        edit-style     {:color (if edit-status "red" "rgba(127, 191, 63, 0.32)")}
        network-status @(re-frame/subscribe [::subs/network-status])
        network-style  {:color (if network-status "rgba(127, 191, 63, 0.32)" "red")}]
    [v-box :children
     [[line]
      [h-box :size "20px" :style status-style :gap "20px" :align :center :children
       [[label :label (str "User: " user-name)]
        [line]
        [label :style network-style :label "Connect Status:"]
        (if network-status
          [md-icon-button :md-icon-name "zmdi-cloud-done" :size :smaller :style network-style]
          [md-icon-button :md-icon-name "zmdi-cloud-off" :size :smaller :style network-style])
        [line]
        [label :style edit-style :label "Edit Status:"]
        [label :style edit-style :label (or edit-status "OK")]]]
      [line]]]))


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
                                  [gap :size "30px"]
                                  [button :label "Access" :on-click process-ok]]]]]])

(defn login
  []
  (let [logged-in  @(re-frame/subscribe [::subs/logged-in])
        form-data  (reagent/atom {:user   "ray"
                                  :secret "warm-blooded-lizards-rock"})
        process-ok (fn [] (re-frame/dispatch [::events/login @form-data]))]
    (fn [] (when-not logged-in
             [modal-panel
              :backdrop-color "lightblue"
              :backdrop-opacity 0.1
              :child [login-form form-data process-ok]]))))

(defn read-panels
  [other-editors]
  (when other-editors
    [v-box :size "auto"
     :children (vec (map #(read-input-panel %) other-editors))]))

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
                [v-split :initial-split "60%"
                 :panel-1 [read-panels other-editors]
                 :panel-2 [edit-panel user-name]])
     :panel-2 [eval-component]]
    [gap :size "10px"]
    [status-bar user-name]]])

(defn main-panel
  []
  (let [user-name     @(re-frame/subscribe [::subs/user-name])
        other-editors @(re-frame/subscribe [::subs/other-editors user-name])]
    (if user-name
      [main-panels user-name other-editors]
      [login])))


;; TODO - enable keymap support for VIM / EMACS
;; which are available from CodeMirror
