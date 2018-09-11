(ns reptile.tongue.events
  (:require
    [cljs.core.specs.alpha]
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx]]
    [reptile.tongue.config :as config]
    [reptile.tongue.db :as db]
    [clojure.string :as str]
    [taoensso.encore :refer [have have?]]
    [taoensso.timbre :refer [tracef debugf infof warnf errorf]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [taoensso.sente.packers.transit :as sente-transit]
    [cljs.reader :as rdr]
    [cljs.tools.reader.reader-types :as treader-types]))

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
      (println "Channel socket state change: %s" new-state-map))))

;; Route changes to the editor code-mirror
(reg-event-fx
  ::update-forms
  (fn [{:keys [db]} [_ update]]
    (when-let [code-mirror (:code-mirror (first (filter #(= (:editor %) (:user update))
                                                        (:editor-code-mirrors db))))]
      (.setValue code-mirror (:form update)))))

(reg-event-db
  ::editors
  (fn [db [_ editors]]
    (assoc db :editors editors)))

(defmethod -event-msg-handler :chsk/recv
  [{:keys [?data]}]
  ; TODO - This works but I think it might be the wrong way to send / process custom events
  (cond
    (= (first ?data) :fast-push/keystrokes)
    (re-frame/dispatch [::update-forms (first (rest ?data))])

    (= (first ?data) :fast-push/editors)
    (re-frame/dispatch [::editors (first (rest ?data))])

    (= (first ?data) :fast-push/eval)
    (re-frame/dispatch [::eval-result (first (rest ?data))])

    (= (first ?data) :chsk/ws-ping)
    nil

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

(reg-event-fx
  ::current-form
  (fn [{:keys [db]} [_ current-form]]
    {:db                 (assoc db :current-form current-form)
     ::send-current-form {:current-form current-form :user-name (:user-name db)}}))

(reg-fx
  ::set-code-mirror-value
  (fn [{:keys [new-value code-mirror]}]
    (.setValue code-mirror new-value)))

(reg-event-fx
  ::from-history
  (fn [{:keys [db]} [_ history-form]]
    (let [code-mirror (:editor-code-mirror db)]
      {:db                     (assoc db :from-history history-form)
       ::set-code-mirror-value {:new-value history-form :code-mirror code-mirror}})))

(defn format-response
  [{:keys [form prepl-response] :as response}]
  (letfn [(format-val [val] (if (nil? val) "nil" val))]
    (cond
      (= 1 (count prepl-response))
      (let [eval-result (first prepl-response)]
        (str form "\n => " (format-val (:val eval-result)) "\n\n"))

      :else
      (let [output      (apply str (doall (map :val
                                               (filter #(not= :ret (:tag %)) prepl-response))))
            eval-result (last prepl-response)]
        (str form "\n" output "=> " (format-val (:val eval-result)) "\n\n")))))

(defn format-results
  [results]
  (doall (map format-response results)))

(reg-event-fx
  ::eval-result
  (fn [{:keys [db]} [_ eval-result]]
    (let [code-mirror  (:eval-code-mirror db)
          eval-results (cons eval-result (:eval-results db))
          str-results  (apply str (reverse (format-results eval-results)))]
      {:db                     (assoc db :eval-results eval-results)
       ::set-code-mirror-value {:new-value   str-results
                                :code-mirror code-mirror}})))

;; Compress code needed for setting of code mirror instances
(reg-event-db
  ::eval-code-mirror
  (fn [db [_ code-mirror]]
    (assoc db :eval-code-mirror code-mirror)))

(reg-event-db
  ::editor-code-mirror
  (fn [db [_ code-mirror]]
    (assoc db :editor-code-mirror code-mirror)))

(reg-event-db
  ::other-editors-code-mirrors
  (fn [db [_ code-mirror editor]]
    (let [code-mirrors (:editor-code-mirrors db)]
      (assoc db :editor-code-mirrors (set (conj code-mirrors {:editor      editor
                                                              :code-mirror code-mirror}))))))

(reg-fx
  ::send-repl-eval
  (fn [[source forms]]
    (doall
         (map (fn
                [form]
                (when-not (str/blank? form)
                  (chsk-send! [:reptile/repl {:form (str form) :source source :forms forms}]
                              (or (:timeout form) 3000))))
              forms))))

(defn read-forms
  "Read the string in the REPL buffer to obtain N forms (rather than just the first!)"
  [repl-forms]
  (let [pbr      (treader-types/string-push-back-reader repl-forms)
        sentinel ::eof]
    (take-while #(not= sentinel %)
                (repeatedly #(rdr/read {:eof sentinel} pbr)))))

(reg-event-fx
  ::eval
  (fn [cofx [_ form-to-eval]]
    {:db              (assoc (:db cofx) :form-to-eval form-to-eval)
     ::send-repl-eval [:user (read-forms form-to-eval)]}))

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

(reg-event-fx
  ::add-lib
  (fn [cofx [_ {:keys [name version url sha maven] :as lib}]]
    (let [use-ns   "(use (quote clojure.tools.deps.alpha.repl))"
          lib-spec (str "(add-lib (quote " (.trim name) ") {"
                        (if maven
                          (str ":mvn/version \"" (.trim version) "\"")
                          (str ":git/url \"" (.trim url) "\" :sha \"" (.trim sha) "\""))
                        "})")]
      {:db              (assoc (:db cofx) :proposed-lib lib)
       ::send-repl-eval [:system [use-ns lib-spec]]})))



