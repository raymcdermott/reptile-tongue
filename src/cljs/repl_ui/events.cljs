(ns repl-ui.events
  (:require
    [cljs.core.specs.alpha]
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx]]
    [repl-ui.db :as db]
    [clojure.string :as str]
    [parinfer-cljs.core :as parinfer]
    [cljs.core.async :as async :refer (<! >! put! chan go go-loop)]
    [taoensso.encore :as encore :refer (have have?)]
    [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
    [taoensso.sente :as sente :refer (cb-success?)]
    [taoensso.sente.packers.transit :as sente-transit]
    [cljs.reader :as edn]))

; use the reframe boot-strap thing to configure initial WS connection

;; --- Obtain OIDC configuration for auth0
;(defn boot-flow
;  []
;  {:first-dispatch [:do-configure-oidc]
;   :rules          [{:when :seen? :events ::success-configure-oidc :dispatch [:auth0-lock-fx] :halt? true}
;                    {:when :seen-any-of? :events [::fail-configure-oidc] :dispatch [:app-failed-state] :halt? true}]})
;
;(reg-event-fx :boot
;              (fn [_ _]
;                {:db         db/default-db
;                 :async-flow (boot-flow)}))


;; (timbre/set-level! :trace) ; Uncomment for more logging

;;;; Define our Sente channel socket (chsk) client

(let [;; Serialization format, must use same val for client + server:
      packer (sente-transit/get-transit-packer)             ; Needs Transit dep

      {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
        "/chsk"                                             ; Must match server Ring routing URL
        {:type   :auto
         ; need to add in the host as it defaults to current server - TODO configure out for dev (OK for PROD)
         :host   "localhost:9090"
         :packer packer})]

  (def chsk chsk)

  ; ChannelSocket's receive channel
  (def ch-chsk ch-recv)

  ; ChannelSocket's send API fn
  (def chsk-send! send-fn)

  ; Watchable, read-only atom
  (def chsk-state state))

;;;;; Sente event handlers

(defmulti -event-msg-handler "Multimethod to handle Sente `event-msg`s"
          ; Dispatch on event-id
          :id)

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default                                                  ; Default/fallback case (no other matching handler)
  [{:keys [event]}]
  (println "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:keys [?data]}]
  (let [[_ new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      ; TODO maybe alert if connection gets dropped
      (println "Channel socket successfully established!: %s" new-state-map)
      (println "Channel socket state change: %s" new-state-map))))

(reg-event-db
  ::update-forms
  (fn [db [_ {:keys [form user-name]}]]
    (assoc-in db [:current-forms user-name] form)))

(reg-event-db
  ::editors
  (fn [db [_ editors]]
    (assoc db :editors editors)))

(defmethod -event-msg-handler :chsk/recv
  [{:keys [?data]}]
  ; TODO - This works but I think it's the wrong way to send / process custom events
  (cond
    (= (first ?data) :fast-push/keystrokes)
    (re-frame/dispatch [::update-forms (first (rest ?data))])

    (= (first ?data) :fast-push/editors)
    (re-frame/dispatch [::editors (first (rest ?data))])

    (= (first ?data) :fast-push/eval)
    (re-frame/dispatch [::eval-result (first (rest ?data))])

    :else (println "Unhandled data push: %s" (first ?data))))

(defmethod -event-msg-handler :chsk/handshake
  [{:keys [?data]}]
  (println "Handshake: %s" ?data))

;; TODO Add your (defmethod -event-msg-handler <event-id> [ev-msg] <body>)s here...


;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))
(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router!
            ch-chsk event-msg-handler)))

;;;; Init stuff
(defn start! [] (start-router!))
(defonce _start-once (start!))


;; Events

(reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

;; Keys -- very simple, only handle one modifier key (Cmd)

(reg-event-db
  ::key-down
  (fn [db [_ key-code]]
    (assoc db :key-down key-code)))

(reg-event-db
  ::key-up
  (fn [db [_ _]]
    (dissoc db :key-down)))

;; Text

(reg-fx
  ::send-current-form
  (fn [{:keys [current-form user-name timeout]}]
    (when-not (str/blank? current-form)
      (chsk-send!
        [:reptile/keystrokes {:form current-form :user-name user-name}]
        (or timeout 3000)))))

(reg-event-db
  ::update-status
  (fn [db [_ status]]
    (assoc db :status status)))

(defn apply-parinfer
  [{:keys [text success? error] :as parinfer-result}]
  (if success?
    text
    (do (re-frame/dispatch [::update-status (:message error)])
        text)))

(reg-event-fx
  ::current-form
  (fn [{:keys [db]} [_ current-form cursor-line cursor-pos]]
    (let [parinfer-result (parinfer/indent-mode current-form {:cursor-line cursor-line
                                                              :cursor-x    cursor-pos})
          parinfer-form (apply-parinfer parinfer-result)]
      (println "::current-form before -> line" cursor-line "pos" cursor-pos)
      (println "::current-form parinfer-result ->" parinfer-result)
      {:db                 (assoc db :current-form current-form
                                     :parinfer-form (or parinfer-form current-form))
       ::send-current-form {:current-form current-form :user-name (:user-name db)}})))

(reg-event-db
  ::eval-result
  (fn [db [_ eval-result]]
    (let [eval-results (or (:eval-results db) [])]
      (assoc db :eval-results (conj eval-results eval-result)))))

(reg-event-fx
  ::eval
  (fn [cofx [_ form-to-eval]]
    (when-not (str/blank? form-to-eval)
      (chsk-send! [:reptile/repl {:form form-to-eval}] (or (:timeout form-to-eval) 3000))
      {:db (assoc (:db cofx) :form-to-eval form-to-eval :eval-result nil)})))

(reg-event-db
  ::login-result
  (fn [db [_ user-name]]
    (assoc db :user-name user-name)))

(reg-fx
  ::login
  (fn [{:keys [user-name timeout]}]
    (when-not (str/blank? user-name)
      (chsk-send!
        [:reptile/login {:proposed-user user-name}] (or timeout 3000)
        (fn [result]
          (if (= result :login-ok)
            (re-frame/dispatch [::login-result user-name])
            (js/alert "Login failed")))))))

(reg-event-fx
  ::login
  (fn [cofx [_ user-name]]
    {:db     (assoc (:db cofx) :proposed-user user-name :user-name nil)
     ::login {:user-name user-name}}))



