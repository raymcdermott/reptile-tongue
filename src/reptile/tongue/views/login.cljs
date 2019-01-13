(ns reptile.tongue.views.login
  (:require
    [reagent.core :as reagent]
    [re-frame.core :as re-frame]
    [re-com.core :refer [h-box v-box button gap border label input-text
                         modal-panel title radio-button]]
    [reptile.tongue.events :as events]
    [reptile.tongue.subs :as subs]))

;; TODO Routing man, come on...

(defn login-form
  [form-data process-ok]
  [border
   :border "1px solid #eee"
   :child [v-box
           :width "400px"
           :size "auto"
           :padding "10px"
           :children
           [[v-box
             :gap "10px"
             :children
             [[title :label "Login to reptile" :level :level2]
              [gap :size "30px"]
              [title :label "User name:" :level :level3]
              [input-text
               :width "100%"
               :model (:user @form-data)
               :placeholder "Your name"
               :on-change #(swap! form-data assoc :user %)]
              [title :label "Team name:" :level :level3]
              [input-text
               :width "100%"
               :model (:team-name @form-data)
               :on-change #(swap! form-data assoc :team-name %)]
              [title :label "Team secret:" :level :level3]
              [input-text
               :width "100%"
               :model (:secret @form-data)
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

(defn authenticate
  []
  (let [logged-in (re-frame/subscribe [::subs/logged-in])
        team-data (re-frame/subscribe [::subs/team-data])]
    (fn []
      (when-not @logged-in
        (let [{:keys [team-name team-secret]} @team-data
              form-data  (reagent/atom {:user      ""
                                        :team-name team-name
                                        :secret    team-secret})
              process-ok (fn [] (re-frame/dispatch [::events/login @form-data]))]
          [modal-panel
           :backdrop-color "lightblue"
           :backdrop-opacity 0.1
           :child [login-form form-data process-ok]])))))

