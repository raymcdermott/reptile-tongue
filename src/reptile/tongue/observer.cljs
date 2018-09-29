(ns reptile.tongue.observer
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                                 md-icon-button input-textarea modal-panel h-split v-split title flex-child-style
                                 radio-button p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [reptile.tongue.events :as events]
            [reptile.tongue.subs :as subs]
            [reagent.core :as reagent]
            [cljsjs.codemirror]
            [cljsjs.codemirror.mode.clojure]
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

(defn other-editor-panels
  [other-editors]
  (if other-editors
    [v-box :size "auto"
     :children (vec (map #(other-editor-panel %) other-editors))]
    [v-box :size "auto"
     :children [[label :label "Waiting for editors"]]]))

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

(defn status-bar
  [user-name]
  (let [network-status @(re-frame/subscribe [::subs/network-status])
        network-style  {:color (if network-status "rgba(127, 191, 63, 0.32)" "red")}]
    [v-box :children
     [[line]
      [h-box :size "20px" :style status-style :gap "20px" :align :center :children
       [[label :label (str "Observer: " user-name)]
        [line]
        [label :style network-style :label "Connect Status:"]
        (if network-status
          [md-icon-button :md-icon-name "zmdi-cloud-done" :size :smaller :style network-style]
          [md-icon-button :md-icon-name "zmdi-cloud-off" :size :smaller :style network-style])]]]]))

(defn observer-panels
  [user-name other-editors]
  (println "Observer mode " user-name)
  [v-box :style {:position "absolute"
                 :top      "18px"
                 :bottom   "0px"
                 :width    "100%"}
   :children
   [[h-split :splitter-size "2px" :initial-split "45%"
     :panel-1 [other-editor-panels other-editors]
     :panel-2 [eval-panel "Guest"]]
    [gap :size "10px"]
    [status-bar user-name]]])
