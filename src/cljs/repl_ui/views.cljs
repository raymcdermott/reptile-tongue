(ns repl-ui.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box gap line scroller border input-textarea
                                 h-split v-split title flex-child-style p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [repl-ui.events :as events]
            [repl-ui.subs :as subs]))

(def panel-style (merge (flex-child-style "1")
                        {:background-color "#fff4f4"
                         :border           "1px solid lightgray"
                         :padding          "0px 20px 0px 20px"}))

(def input-style (merge (flex-child-style "1")
                        {:background-color "#f2f2f2"
                         :border           "1px solid lightgray"
                         :padding          "0px 20px 0px 20px"}))

(defn heading []
  (let [name (re-frame/subscribe [::subs/name])]
    [title
     :label (str "REPtiLe by " @name)
     :level :level1]))

(defn splitter-panel-title
  [text]
  [title
   :label text
   :level :level3
   :style {:margin-top "20px"}])

(defn read-panel
  [panel-name]
  [v-box
   :size "auto"
   :children
   [[box
     :size "auto"
     :child [:div {:style panel-style :id (str panel-name "-out")}
             [splitter-panel-title [:code panel-name]]]]
    [box
     :size "auto"
     :child [:div {:style panel-style :id (str panel-name "-in")}]]]])

(defn edit-panel
  [panel-name]
  (let [evalled (re-frame/subscribe [::subs/eval-items])]
    [v-split
     :splitter-size "3px"
     :panel-1 [box
               :size "auto"
               :child [:div {:style input-style :id (str panel-name "-out")}
                       [splitter-panel-title [:code panel-name]]
                       (when @evalled
                         [p @evalled])]]
     :panel-2 [box
               :size "auto"
               :child [:textarea {:id              (str panel-name "-repl")
                                  :placeholder     "(type clojure here)"
                                  :size            "0 0 5000px"
                                  :change-on-blur? false
                                  :style           input-style
                                  ;; check for cmd-enter to send to server
                                  :on-change       #(re-frame/dispatch [::events/form-to-eval
                                                                        (-> % .-currentTarget .-value)])}]]]))

(defn box-panels
  [names]
  [h-box
   :size "auto"
   :children [(map read-panel names)]])

(defn main-panel []
  [v-box
   :height "800px"
   :children [[heading]
              [h-split
               :splitter-size "3px"
               :initial-split "30%"
               :panel-1 [edit-panel "Ray"]
               :panel-2 [box-panels ["Mia" "Eric" "Mike"]]]]])


