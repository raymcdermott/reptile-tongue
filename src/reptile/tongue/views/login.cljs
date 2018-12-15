(ns reptile.tongue.views.login
  (:require
    [reagent.core :as reagent]
    [re-frame.core :as re-frame]
    [re-com.core :refer [h-box v-box button gap border label input-text
                         modal-panel title radio-button]]
    [reptile.tongue.events :as events]
    [reptile.tongue.subs :as subs]))

(defn login-form
  [form-data process-ok]
  [border
   :border "1px solid #eee"
   :child [v-box
           :size "auto"
           :padding "10px"
           :children
           [[v-box
             :gap "10px"
             :children
             [[title :label "Login to reptile" :level :level2]
              [gap :size "30px"]
              [label :label "User name"]
              [input-text
               :model (:user @form-data)
               :placeholder "Your name"
               :on-change #(swap! form-data assoc :user %)]
              [label :label "Shared secret"]
              [input-text
               :model (:secret @form-data)
               :placeholder "The secret you know"
               :on-change #(swap! form-data assoc :secret %)]
              [gap :size "30px"]
              [h-box
               :size "auto"
               :children
               [[button :label "Access" :class "btn-success"
                 :on-click process-ok]
                [gap :size "100px"]
                [:img {:alt   "Welcome to reptile"
                       :width "75px" :height "75px"
                       :src   "/images/reptile-logo-yellow-transparent.png"}]]]]]]]])

; TODO - have a flag so that we only pre-fill secret in dev mode
(defn authenticate
  []
  (let [logged-in  @(re-frame/subscribe [::subs/logged-in])
        form-data  (reagent/atom {:user "" :secret "warm-blooded-lizards-rock"})
        process-ok (fn [] (re-frame/dispatch [::events/login @form-data]))]
    (fn []
      (when-not logged-in
        [modal-panel
         :backdrop-color "lightblue"
         :backdrop-opacity 0.1
         :child [login-form form-data process-ok]]))))

