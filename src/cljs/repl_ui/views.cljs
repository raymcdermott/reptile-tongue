(ns repl-ui.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text
                                 input-textarea h-split v-split title flex-child-style p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [repl-ui.events :as events]
            [repl-ui.subs :as subs]
            [cljs.reader :as edn]))

(def default-style {:font-family "Menlo, Lucida Console, Monaco, monospace"
                    :border      "1px solid lightgray"
                    :padding     "10px 20px 0px 20px"})

(def panel-style (merge (flex-child-style "1")
                        default-style {:background-color "#e8e8e8"}))

(def input-style (merge (flex-child-style "1")
                        default-style {:resize "none"}))

(def eval-style (merge (flex-child-style "1") default-style))

(defn splitter-panel-title [text]
  [title :label text :level :level3 :style {:margin-top "20px"}])

(defn read-input-panel [user-name]
  (let [current-form @(re-frame/subscribe [::subs/user-keystrokes user-name])]
    [v-box :size "auto" :children
     [[box :size "auto" :child
       [:div {:style panel-style :id (str user-name "-out")}
        [label :label current-form]
        [splitter-panel-title [:code user-name]]]]]]))

(defn format-result [result]
  (let [form (or (first (edn/read-string (:pretty result))) (:form result))]
    (list [label :label form]
          [label :label (str "=> " (:val result)) :on-click #(js/alert result)]
          [gap :size "10px"])))

(defn eval-panel []
  (let [eval-results @(re-frame/subscribe [::subs/eval-results])]
    [scroller :child
     ; TODO make CSS dynamic so that we don't lose it when scrolling
     [box :size "auto" :child
      [:div {:style eval-style}
       (when eval-results
         [v-box :size "auto" :children
          (map format-result eval-results)])]]]))

(defn edit-panel [panel-name]
  (let [user-name @(re-frame/subscribe [::subs/user-name])
        parinfer-form @(re-frame/subscribe [::subs/parinfer-form])]
    [scroller :child
     [v-box :size "auto" :children
      [[box :size "auto" :child
        [:textarea {:id          panel-name
                    :placeholder (str "(your clojure here) - " user-name)
                    :size        "0 0 5000px"
                    :style       input-style
                    ;; check for cmd-enter to send to server
                    :on-change   #(re-frame/dispatch
                                    [::events/current-form (-> % .-currentTarget .-value)])
                    :value       parinfer-form}]]

       [button :label "Eval"
        :on-click #(re-frame/dispatch
                     [::events/eval (.-value (.getElementById js/document panel-name))])]]]]))

(defn login-panel []
  [h-box :size "auto" :children
   [[input-text :model "" :placeholder "user-name" :style input-style :attr {:id "login-panel"}
     :on-change #(-> nil)]
    [button :label "Login"
     :on-click #(re-frame/dispatch
                  [::events/login (.-value (.getElementById js/document "login-panel"))])]]])

(defn box-panels []
  (let [user-name @(re-frame/subscribe [::subs/user-name])
        other-editors @(re-frame/subscribe [::subs/other-editors user-name])]
    [h-split :margin "2px"
     :panel-1 [v-split
               :panel-1 [read-input-panel (or (nth other-editors 0 "0"))]
               :panel-2 (if user-name
                          [edit-panel user-name]
                          [login-panel])]
     :panel-2 [v-split
               :panel-1 [read-input-panel (or (nth other-editors 1 "1"))]
               :panel-2 [read-input-panel (or (nth other-editors 2 "2"))]]]))

(defn main-panel []
  [v-box :height "800px" :children
   [[v-split :splitter-size "3px" :initial-split "60%"
     :panel-1 [eval-panel]
     :panel-2 [box-panels]]]])
