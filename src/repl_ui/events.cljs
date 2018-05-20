(ns repl-ui.events
  (:require
    [cljs.core.specs.alpha]
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx]]
    [repl-ui.config :as config]
    [repl-ui.db :as db]
    [clojure.string :as str]
    [parinfer-cljs.core :as parinfer]
    [cljs.core.async :as async :refer (<! >! put! chan go go-loop)]
    [taoensso.encore :as encore :refer (have have?)]
    [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
    [taoensso.sente :as sente :refer (cb-success?)]
    [taoensso.sente.packers.transit :as sente-transit]
    [cljs.reader :as edn]))

;; (timbre/set-level! :trace) ; Uncomment for more logging

(declare chsk ch-chsk chsk-send! chsk-state)


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
    (re-frame/dispatch [::network-status (:open? new-state-map)])
    (if (:first-open? new-state-map)
      (println "Channel socket successfully established!: %s" new-state-map)
      (println "XXXX Channel socket state change: %s" new-state-map))))

(reg-event-db
  ::update-forms
  (fn [db [_ update]]
    (assoc-in db [:current-forms (keyword (:user update))] (:form update))))

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

(reg-event-db
  ::key-press
  (fn [db [_ key-code]]
    (assoc db :key-press key-code)))


;; Text

(reg-fx
  ::send-current-form
  (fn [{:keys [current-form user-name timeout]}]
    (when-not (str/blank? current-form)
      (chsk-send!
        [:reptile/keystrokes {:form current-form :user-name user-name}]
        (or timeout 3000)))))

(reg-event-db
  ::network-status
  (fn [db [_ status]]
    (assoc db :network-status status)))

(reg-event-db
  ::update-status
  (fn [db [_ status]]
    (assoc db :status status)))

(defn apply-parinfer
  [{:keys [text success? error]}]
  (when-not success? (re-frame/dispatch [::update-status (:message error)]))
  text)

(reg-event-fx
  ::current-form
  (fn [{:keys [db]} [_ current-form cursor-line cursor-pos prev-cursor-line prev-cursor-x]]
    (let [enter-pressed?  (= (:key-press db) 13)
          parinfer-mode   (if enter-pressed? parinfer/paren-mode parinfer/indent-mode)
          parinfer-result (parinfer-mode current-form {:cursor-line      cursor-line
                                                       :cursor-x         cursor-pos
                                                       :prev-cursor-line prev-cursor-line
                                                       :prev-cursor-x    prev-cursor-x})
          parinfer-form   (apply-parinfer parinfer-result)]
      {:db                 (assoc db :current-form current-form
                                     :parinfer-form (or parinfer-form current-form)
                                     :parinfer-result parinfer-result)
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

(let [;; Serialization format, must use same val for client + server:
      packer (sente-transit/get-transit-packer)             ; Needs Transit dep

      {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
        "/chsk"                                             ; Must match server Ring routing URL
        {:type   :auto
         :host   config/server-host
         :packer packer})]

  (println "server-connect chsk" chsk "\nch-recv" ch-recv "\nsend-fn" send-fn "\nstate" state)

  (def chsk chsk)

  ; ChannelSocket's receive channel
  (def ch-chsk ch-recv)

  ; ChannelSocket's send API fn
  (def chsk-send! send-fn)

  ; Watchable, read-only atom
  (def chsk-state state)

  ;; Now we have all of this set up we can start the router
  (start-router!))

(reg-fx
  ::server-login
  (fn [{:keys [login-options timeout]}]
    (chsk-send! [:reptile/login login-options] (or timeout 3000)
                (fn [result]
                  (if (= result :login-ok)
                    (re-frame/dispatch [::login-result (:user login-options)])
                    (js/alert "Login failed"))))))

(reg-event-fx
  ::login
  (fn [cofx [_ login-options]]
    {:db            (assoc (:db cofx) :proposed-user (:user login-options) :user-name nil)
     ; {:user   "YOUR-NAME" :server-url "https://some-ec2-server.aws.com" :secret "6738f275-513b-4ab9-8064-93957c4b3f35"}
     ::server-login {:login-options login-options}}))

