(ns reptile.tongue.views.eval
  (:require
    [re-frame.core :as re-frame]
    [re-com.core :refer [h-box v-box box button gap line border label input-text
                         md-circle-icon-button md-icon-button input-textarea modal-panel
                         h-split v-split title flex-child-style radio-button p]]
    [reagent.core :as reagent]
    [reptile.tongue.auth0 :as auth0]
    [reptile.tongue.subs :as subs]
    [reptile.tongue.events :as events]
    [reptile.tongue.code-mirror :as code-mirror]))

(defonce eval-panel-style (merge (flex-child-style "1")
                                 {:border  "1px solid lightgray"
                                  :padding "5px 5px 5px 5px"}))

(defonce eval-component-style (merge (flex-child-style "1")
                                     {:padding "15px 5px 0px 5px"}))

(defn eval-did-mount
  []
  (fn [this]
    (letfn [(scrollToBottom [cm]
              (.scrollIntoView cm #js {:line (.lastLine cm)}))]
      (let [node        (reagent/dom-node this)
            options     {:options {:lineWrapping true
                                   :readOnly     true}}
            code-mirror (code-mirror/parinfer node options)]
        (.on code-mirror "change" #(scrollToBottom code-mirror))
        (re-frame/dispatch [::events/eval-code-mirror code-mirror])))))

(defn eval-component
  [panel-name]
  (reagent/create-class
    {:component-did-mount  (eval-did-mount)
     :reagent-render       (code-mirror/text-area (str "eval-" panel-name))
     :component-did-update #(-> nil)                        ; noop to prevent reload
     :display-name         "eval"}))

(defn gist-render
  []
  (fn [this]
    [md-icon-button
     :tooltip "Save session to a GIST"
     :md-icon-name "zmdi-github"
     :on-click #(identity %)]))

(defn auth0-inner
  []
  (reagent/create-class
    {:component-did-mount  (identity 1)                            ;(re-frame/dispatch-sync [::auth0/login-config])
     :reagent-render       (gist-render)
     :component-did-update (identity 1)                            ; noop to prevent reload
     :display-name         "gist-eval"}))

(defn auth0-outer
  []
  )

(defn eval-panel
  [panel-name]
  (let [show-times? @(re-frame/subscribe [::subs/show-times])
        auth-result @(re-frame/subscribe [::subs/auth-result])]
    (fn []
      [v-box :size "auto" :style eval-panel-style
       :children
       [[h-box :align :center :justify :end :gap "20px" :height "20px"
         :children
         [[md-icon-button
           :tooltip "Show evaluation times"
           :md-icon-name "zmdi-timer"
           :style {:color (if show-times? "red" "black")}
           :on-click #(re-frame/dispatch [::events/show-times (if show-times?
                                                                (false? show-times?)
                                                                true)])]
          [md-icon-button
           :tooltip "Clear evaluations"
           :md-icon-name "zmdi-delete"
           :on-click #(re-frame/dispatch [::events/clear-evals])]]]
        [box :size "auto" :style eval-component-style
         :child [eval-component panel-name]]]])))
