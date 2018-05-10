(ns repl-ui.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-icon-button
                                 input-textarea modal-panel h-split v-split title flex-child-style p]]
            [re-com.splits :refer [hv-split-args-desc]]
            [repl-ui.events :as events]
            [repl-ui.subs :as subs]
            [cljs.reader :as edn]
            [reagent.core :as reagent]
            [parinfer-cljs.core :as parinfer]
            [clojure.string :as str]))

(def default-style {:font-family "Menlo, Lucida Console, Monaco, monospace"
                    :border      "1px solid lightgray"
                    :padding     "10px 20px 0px 20px"})

(def readonly-panel-style (merge
                            (flex-child-style "1")
                            default-style
                            {:background-color "#e8e8e8"
                             :resize           "none"}))

(def input-style (merge
                   (flex-child-style "1")
                   default-style
                   {:resize "none"}))

(def eval-panel-style (merge (flex-child-style "1")
                             default-style))

(def eval-content-style (merge (flex-child-style "1")
                               {:resize    "none"
                                :flex-flow "column-reverse nowrap"
                                :padding   "10px 10px 10px 10px"}))

(def history-style {:padding "5px 5px 0px 10px"})

(def status-style (merge default-style {:color "lightgrey"}))

; TODO use background image
; :background-image "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' version='1.1' height='50px' width='120px'><text x='0' y='15' fill='red' font-size='20'>I love SVG!</text></svg>"

(defn read-input-panel
  [user-name]
  (let [current-form @(re-frame/subscribe [::subs/user-keystrokes (keyword user-name)])]
    [v-box :size "auto" :children
     [[:textarea
       {:id          (str "read-input-panel-" user-name)
        :placeholder user-name
        :style       readonly-panel-style
        :disabled    true
        :on-change   #(-> nil)
        :value       (str user-name "\n"
                          (when current-form
                            (:text (parinfer/paren-mode current-form))))}]]]))

(defn format-trace
  [via trace]
  (let [show-trace? (reagent/atom false)]
    (fn
      []
      [v-box
       :children
       [[md-icon-button :md-icon-name "zmdi-zoom-in"
         :tooltip (:at (first via))
         :on-click #(reset! show-trace? true)]
        (when @show-trace?
          [modal-panel
           :backdrop-on-click #(reset! show-trace? false)
           :child
           [scroller :child
            [v-box :size "auto"
             :children
             [(map (fn [element]
                     [label :label (str element)])
                   trace)
              [gap :size "20px"]
              [button :label "Done (or click the backdrop)"
               :on-click #(reset! show-trace? false)]]]]])]])))

(defn format-exception
  [{:keys [val original-form]}]
  (let [{:keys [cause via trace]} (edn/read-string val)]
    [v-box :size "auto"
     :children [[label :label original-form]
                [h-box :size "auto"
                 :children [[label :style {:color "red"} :label (str "=> " cause)]
                            [gap :size "20px"]
                            [format-trace via trace]]]
                [gap :size "20px"]]]))

(defn format-result
  [result]
  (let [val  (try (edn/read-string (:val result))
                  (catch js/Error _ (:val result)))
        form (or (first (edn/read-string (:pretty result))) (:form result))]
    (if (and (map? val) (= #{:cause :via :trace} (set (keys val))))
      [format-exception result]
      [v-box :size "auto" :children
       [[label :label form]
        [label :label (str "=> " (:val result))]
        [gap :size "20px"]]])))

(defn eval-panel
  []
  (reagent/create-class
    {:component-did-update
     (fn event-expression-component-did-update [this]
       (let [node (reagent/dom-node this)]                  ;; Keep the content scrolling from the bottom
         (set! (.-scrollTop node) (.-scrollHeight node))))

     :display-name
     "eval-component"

     :reagent-render
     (fn
       []
       (let [eval-results @(re-frame/subscribe [::subs/eval-results])]
         [scroller :style eval-panel-style
          :child
          [box :style eval-content-style :size "auto"
           :child
           [:div (when eval-results
                   [v-box :size "auto" :children
                    (map format-result eval-results)])]]]))}))

(defn format-history
  [historical-form]
  [md-icon-button :md-icon-name "zmdi-comment-text" :tooltip historical-form
   :on-click #(re-frame/dispatch [::events/current-form historical-form])])


(defn edit-component
  [panel-name]
  (let [pre-cursor (atom {:cursor-x 0 :cursor-line 0 :text ""})
        new-cursor (atom {:cursor-x 0 :cursor-line 0 :text ""})]
    (reagent/create-class
      {:component-did-mount
       (fn event-expression-component-did-mount [this]
         (.focus (reagent/dom-node this)))

       :component-did-update
       (fn event-expression-component-did-update [this]
         (let [node   (reagent/dom-node this)
               pre-x  (:cursor-x @pre-cursor)
               {:keys [cursor-x text cursor-line]} @new-cursor
               offset (+ cursor-x cursor-line (reduce + (map #(-> text
                                                                  str/split-lines
                                                                  (nth %)
                                                                  count)
                                                             (range cursor-line))))]
           ;; Set the focus on the text area
           (.focus node)

           ;; Move the cursor
           (when (not= pre-x cursor-x)
             (reset! pre-cursor @new-cursor)
             (.setSelectionRange node offset offset))))

       :display-name
       "edit-component"

       :reagent-render
       (fn
         [panel-name]
         (let [parinfer-form   @(re-frame/subscribe [::subs/parinfer-form])
               parinfer-cursor @(re-frame/subscribe [::subs/parinfer-cursor])
               key-down        @(re-frame/subscribe [::subs/key-down])]
           (reset! new-cursor parinfer-cursor)
           [:textarea
            {:id            panel-name
             :auto-complete false
             :placeholder   (str "(your clojure here) - " panel-name)
             :style         input-style

             ;; Dispatch on Alt-Enter
             :on-key-down   #(re-frame/dispatch [::events/key-down (.-which %)])
             :on-key-up     #(re-frame/dispatch [::events/key-up (.-which %)])
             :on-key-press  #(do (re-frame/dispatch [::events/key-press (.-which %)])
                                 (when (and (= (.-which %) 13) (= key-down 18))
                                   (re-frame/dispatch [::events/eval (-> % .-currentTarget .-value)])))

             :on-change     #(let [current-value   (-> % .-currentTarget .-value)
                                   selection-start (-> % .-currentTarget .-selectionStart)
                                   cursor-line     (-> (subs current-value 0 selection-start)
                                                       str/split-lines count dec)
                                   cursor-pos      (-> (subs current-value 0 selection-start)
                                                       str/split-lines last count)]
                               (re-frame/dispatch [::events/current-form current-value
                                                   cursor-line cursor-pos
                                                   (:cursor-line @pre-cursor) (:cursor-x @pre-cursor)]))
             :value         parinfer-form}]))})))

(defn edit-panel
  [panel-name]
  (let [eval-results @(re-frame/subscribe [::subs/eval-results])]
    [v-box :size "auto"
     :children
     [[box :size "auto" :child
       [scroller :child
        [edit-component panel-name]]]
      [gap :size "5px"]
      [h-box
       :children
       [[button :label "Eval (or Alt-Enter)" :on-click
         #(re-frame/dispatch [::events/eval (.-value (.getElementById js/document panel-name))])]
        [gap :size "30px"]
        (when eval-results                                  ; visualise and give access to history
          [h-box :size "auto" :align :center :style history-style :children
           (reverse (map format-history (distinct (map :original-form eval-results))))])]]]]))

(defn status-bar
  [user-name]
  (let [status @(re-frame/subscribe [::subs/status])]
    [h-box
     :size "40px" :gap "20px" :style status-style
     :children
     [[label :label (str "User: " user-name)]
      [line]
      [label :label "Edit Status:"]
      (let [style {:color (if status "red" "green")}]
        [label :style style :label (or status "OK")])]]))

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
        form-data  (reagent/atom {:user   "?"
                                  :secret "6738f275-513b-4ab9-8064-93957c4b3f35"})
        process-ok (fn []
                     (re-frame/dispatch [::events/login @form-data]))]
    (fn [] (when-not logged-in
             [modal-panel
              :backdrop-color "lightblue"
              :backdrop-opacity 0.1
              :child [login-form form-data process-ok]]))))

(defn box-panels
  []
  (let [user-name     @(re-frame/subscribe [::subs/user-name])
        other-editors @(re-frame/subscribe [::subs/other-editors user-name])]
    [v-split :initial-split "40%"
     :panel-1 [read-input-panel (or (nth other-editors 0 "?"))]
     :panel-2 [edit-panel user-name]]))


(defn main-panel
  []
  (let [user-name @(re-frame/subscribe [::subs/user-name])]
    (if user-name
      [v-box :height "1000px"
       :children
       [[v-split :splitter-size "3px"
         :panel-1 [eval-panel]
         :panel-2 [box-panels]]
        [gap :size "20px"]
        [status-bar user-name]]]
      [login])))


