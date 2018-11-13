(ns reptile.tongue.auth0
  (:require [re-frame.core :refer [reg-event-db reg-event-fx reg-fx dispatch]]
            [day8.re-frame.http-fx]
            [cljsjs.auth0-lock]
            [ajax.core :refer [json-response-format]]))

;;;; TODO - set up the gist item as a 'special' component

;; --- Establish the auth0 lock and authentication callback
(reg-fx
  ::auth0-init
  (fn [lock]
    (.on lock "authenticated" #(dispatch [::set-auth-result %]))))

(reg-event-fx
  ::login-config
  (fn [{:keys [db]} _]
    (let [{:keys [domain client-id audience]} (:auth0-config db)
          options (clj->js {:redirect     false
                            :audience     audience
                            :responseType "token"
                            :scope        "openid"})
          lock    (js/Auth0Lock. client-id domain #_options)]
      {:db          (assoc db :auth0-lock lock)
       ::auth0-init lock})))

(reg-event-db
  ::set-gist-user-info
  (fn [db [_ user-info]]
    (assoc db :gist-user-info user-info)))

(defn get-user-callback
  "Handle the response for Auth0 getUserInfo request"
  [error user-info]
  (println :get-user-callback user-info)
  (if error
    (js/alert (str "There was an error - we should handle that: " error))
    (let [gist-user-info (js->clj user-info :keywordize-keys true)]
      (dispatch [::set-gist-user-info gist-user-info]))))

(reg-fx
  ::get-auth0-profile
  (fn [[lock auth-result]]
    (println :get-auth0-profile auth-result)
    (let [access-token (:access-token auth-result)]
      (.getUserInfo lock access-token get-user-callback))))

(defn normalize-auth0-auth-data
  [auth-result]
  (let [{:keys [accessToken refreshToken]}
        (js->clj auth-result :keywordize-keys true)]
    {:access-token  accessToken
     :refresh-token refreshToken}))

;;; events
(reg-event-fx
  ::set-auth-result
  (fn [{:keys [db]} [_ auth-result]]
    (println :set-auth-result auth-result)
    (let [lock        (:auth0-lock db)
          auth-result (normalize-auth0-auth-data auth-result)]
      {:db                 (assoc db :auth-result auth-result)
       ::get-auth0-profile [lock auth-result]})))

(reg-fx
  ::auth0-logout
  (fn [[lock return-url]]
    (.logout lock (clj->js {:returnTo return-url}))))

(reg-event-fx
  ::gist-logout
  (fn [{:keys [db]}]
    (let [lock (:auth0-lock db)]
      {:db            (assoc db :auth0-lock-active false)
       ::auth0-logout [lock (str (get-in db [:browser-config :url]))]})))

(reg-fx
  ::auth0-login
  (fn [lock]
    (.show lock)))

(reg-event-fx
  ::gist-login
  (fn [{:keys [db]}]
    {:db           (assoc db :auth0-lock-active true)
     ::auth0-login (:auth0-lock db)}))



