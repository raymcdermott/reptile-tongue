(ns reptile.tongue.views.login
  (:require
    [reagent.core :as reagent]
    [re-frame.core :as re-frame]
    [re-com.core :refer [v-box button gap border label input-text
                         modal-panel title radio-button]]
    [reptile.tongue.events :as events]
    [reptile.tongue.subs :as subs]))

(defn login-form
  [form-data process-ok]
  [border
   :border "1px solid #eee"
   :child [v-box
           :size "auto"
           :gap "30px" :padding "10px"
           :children
           [[title :label "Welcome to REPtiLe" :level :level2]
            [v-box
             :gap "10px"
             :children
             [[label :label "User name"]
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

; TODO - have a different key for observers rather selecting it on a form
(defn authenticate
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

