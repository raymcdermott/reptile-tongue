(ns reptile.tongue.events
  (:require
    [cljs.core.specs.alpha]
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx]]
    [reptile.tongue.config :as config]
    [reptile.tongue.db :as db]
    [clojure.string :as str]
    [taoensso.encore :as encore :refer [have have?]]
    [taoensso.timbre :refer [tracef debugf infof warnf errorf]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [taoensso.sente.packers.transit :as sente-transit]
    [cljs.reader :as rdr]
    [taoensso.timbre :as timbre]))

;(timbre/set-level! :trace)                                  ; Uncomment for more logging

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
                                                        (:other-editor-code-mirrors db))))]
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

;; Text

(reg-fx
  ::send-current-form
  (fn [{:keys [current-form user-name timeout]}]
    (when-not (str/blank? current-form)
      (chsk-send! [:reptile/keystrokes {:form      current-form
                                        :user-name user-name}]
                  (or timeout 3000)))))

(reg-event-db
  ::network-status
  (fn [db [_ status]]
    (assoc db :network-status status)))

(reg-event-fx
  ::current-form
  (fn [{:keys [db]} [_ current-form]]
    {:db                 (assoc db :current-form current-form)
     ::send-current-form {:current-form current-form
                          :user-name    (:user-name db)}}))

(reg-fx
  ::set-code-mirror-value
  (fn [{:keys [new-value code-mirror]}]
    (.setValue code-mirror new-value)))

(reg-event-fx
  ::from-history
  (fn [{:keys [db]} [_ history-form]]
    (let [code-mirror (:editor-code-mirror db)]
      {:db                     (assoc db :from-history history-form)
       ::set-code-mirror-value {:new-value   history-form
                                :code-mirror code-mirror}})))

(defn read-ex
  "Read exceptions, patching spec SNAFUs"
  [exc]
  (try (some-> exc
               (clojure.string/replace #"^#error " "")
               (clojure.string/replace #":spec #object.+ :value" ":value")
               (rdr/read-string))
       (catch :default e (do (println "read-ex error:" e "input form" exc)
                             (str "read-ex error:" e)))))

(defn pred-fails
  [spec-error]
  (some->> (get-in spec-error [:data :clojure.spec.alpha/problems])
           (map :pred)
           (distinct)
           (interpose "\n")
           (apply str)))

(defn format-nil-vals
  [val]
  (if (nil? val) "nil" val))

(defn format-return-vals
  [response]
  (some->> response
           (filter #(= :ret (:tag %)))
           (map :val)
           (map format-nil-vals)
           (interpose "\n=> ")
           (apply str)))

(defn format-response
  [result]
  (let [{:keys [val tag cause]} result
        spec-err   (and (= :err tag) (read-ex val))
        exception? (or (:cause spec-err) cause)]
    (cond
      exception?
      (str "=> " exception?
           "\n" (when-let [fails (pred-fails spec-err)] (str "Failure on: " fails "\n"))
           "\n")

      (= tag :out)
      val

      (= tag :ret)
      (str "=> " (format-nil-vals val) "\n"))))

(defn format-responses
  [{:keys [form prepl-response]}]
  (str form "\n" (doall (apply str (map format-response prepl-response)))))

(defn format-results
  [results]
  (doall (map format-responses results)))

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
    (let [code-mirrors    (:other-editor-code-mirrors db)
          code-mirror-set (set (conj code-mirrors {:editor      editor
                                                   :code-mirror code-mirror}))]
      (assoc db :other-editor-code-mirrors code-mirror-set))))

(reg-fx
  ::send-repl-eval
  (fn [[source form]]
    (when-not (str/blank? form)
      (chsk-send! [:reptile/repl {:form   form
                                  :source source
                                  :forms  form}]
                  (or (:timeout form) 3000)))))

(reg-event-fx
  ::eval
  (fn [cofx [_ form-to-eval]]
    {:db              (assoc (:db cofx) :form-to-eval form-to-eval)
     ::send-repl-eval [:user form-to-eval]}))

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
    {:db            (assoc (:db cofx) :proposed-user (:user login-options)
                                      :observer (:observer login-options)
                                      :user-name nil)
     ::server-login {:login-options login-options}}))

(reg-event-fx
  ::add-lib
  (fn [cofx [_ {:keys [name version url sha maven] :as lib}]]
    (let [use-ns   "(use 'clojure.tools.deps.alpha.repl)"
          lib-spec (str "(add-lib '" (.trim name) " {"
                        (if maven
                          (str ":mvn/version \"" (.trim version) "\"")
                          (str ":git/url \"" (.trim url) "\" :sha \"" (.trim sha) "\""))
                        "})")]
      {:db              (assoc (:db cofx) :proposed-lib lib)
       ::send-repl-eval [:system (str use-ns "\n" lib-spec)]})))



