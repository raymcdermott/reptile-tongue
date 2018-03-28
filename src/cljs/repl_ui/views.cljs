(ns repl-ui.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-icon-button
                                 input-textarea h-split v-split title flex-child-style p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [repl-ui.events :as events]
            [repl-ui.subs :as subs]
            [cljs.reader :as edn]))

(def default-style {:font-family "Menlo, Lucida Console, Monaco, monospace"
                    :border      "1px solid lightgray"
                    :padding     "10px 20px 0px 20px"})

(def panel-style (merge (flex-child-style "1") default-style
                        {:background-color "#e8e8e8"}))

(def input-style (merge (flex-child-style "1") default-style
                        {:resize "none"}))

(def eval-panel-style (merge (flex-child-style "1") default-style))

(def eval-content-style (merge (flex-child-style "1")
                               {:resize    "none"
                                :flex-flow "column-reverse nowrap"
                                :padding   "10px 10px 10px 10px"}))

(def history-style {:padding "5px 5px 0px 10px"})

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
    [v-box :size "auto" :children
     [[label :label form]
      [label :label (str "=> " (:val result)) :on-click #(js/alert result)]
      [gap :size "20px"]]]))


(defn eval-panel []
  (let [eval-results @(re-frame/subscribe [::subs/eval-results])]
    [scroller :style eval-panel-style :child
     [box :style eval-content-style :size "auto" :child
      [:div (when eval-results [v-box :size "auto" :children
                                (map format-result eval-results)])]]]))

(defn format-history [historical-form]
  [md-icon-button :md-icon-name "zmdi-caret-up-circle" :tooltip historical-form
   :on-click #(re-frame/dispatch [::events/current-form historical-form])])

(defn edit-panel [panel-name]
  (let [user-name @(re-frame/subscribe [::subs/user-name])
        parinfer-form @(re-frame/subscribe [::subs/parinfer-form])
        eval-results @(re-frame/subscribe [::subs/eval-results])]
    [v-box :size "auto" :children
     [[box :size "auto" :child
       [scroller :child
        [:textarea {:id          panel-name
                    :placeholder (str "(your clojure here) - " user-name)
                    :style       input-style
                    ;; TODO: check for cmd-enter to also send to server for eval
                    :on-change   #(re-frame/dispatch
                                    [::events/current-form (-> % .-currentTarget .-value)])
                    :value       parinfer-form}]]]
      [h-box :children
       [[button :label "Eval" :on-click
         #(re-frame/dispatch [::events/eval (.-value (.getElementById js/document panel-name))])]
        [gap :size "30px"]
        (when eval-results                                  ; visualise and give access to history
          [h-box :size "auto" :align :center :style history-style :children
           (reverse (map format-history (distinct (map :original-form eval-results))))])]]]]))

(defn login-panel []
  [h-box :size "auto" :children
   [[input-text :model "" :placeholder "user-name" :style input-style :attr {:id "login-panel"}
     :on-change #(-> nil)]
    [button :label "Login" :on-click
     #(re-frame/dispatch [::events/login (.-value (.getElementById js/document "login-panel"))])]]])

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
