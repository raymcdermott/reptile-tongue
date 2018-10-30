(ns reptile.tongue.events
  (:require
    goog.date.Date
    [cljs.core.specs.alpha]
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx]]
    [taoensso.encore :as encore :refer [have have?]]
    [taoensso.timbre :refer [tracef debugf infof warnf errorf]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [taoensso.sente.packers.transit :as sente-transit]
    [taoensso.timbre :as timbre]
    [cljs.tools.reader.edn :as rdr]
    [cljs.core.async :as async]
    [clojure.string :as str]
    [reptile.tongue.config :as config]
    [reptile.tongue.db :as db]
    [reptile.tongue.code-mirror :as code-mirror]))

;(timbre/set-level! :trace)                                  ; Uncomment for more logging

; --- WS client ---
(declare chsk ch-chsk chsk-send! chsk-state)

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

(defmethod -event-msg-handler :chsk/recv
  [{:keys [?data]}]
  (let [push-event (first ?data)
        push-data  (first (rest ?data))]
    (cond
      (= push-event :fast-push/keystrokes)
      (re-frame/dispatch [::network-repl-editor-form-update push-data])

      (= push-event :fast-push/editors)
      (re-frame/dispatch [::repl-editors push-data])

      (= push-event :fast-push/eval)
      (re-frame/dispatch [::eval-result push-data])

      (= push-event :chsk/ws-ping)
      :noop                                                 ; do reply

      :else (println "Unhandled data push: %s" push-event))))

(defmethod -event-msg-handler :chsk/handshake
  [{:keys [?data]}]
  (println "Handshake: %s" ?data))

(defonce router_ (atom nil))
(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router!
            ch-chsk event-msg-handler)))

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

; --- Events ---
(reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  ::network-status
  (fn [db [_ status]]
    (assoc db :network-status status)))

(defn pred-fails
  [problems]
  (some->> problems
           (map #(str "🤔  " (:val %) " is not " (:pred %)))
           (interpose "\n")
           (apply str)))

(defn format-nil-vals
  [val]
  (if (nil? val) "nil" val))

(defn default-reptile-tag-reader
  [tag val]
  {:nk-tag tag :nk-val (rdr/read-string (str val))})

(defn check-exception
  [val]
  (when (str/starts-with? val "{:cause")
    (let [reader-opts {:default default-reptile-tag-reader}
          {:keys [cause via trace data]} (rdr/read-string reader-opts val)
          problems    (:clojure.spec.alpha/problems data)
          spec        (:clojure.spec.alpha/spec data)
          value       (:clojure.spec.alpha/value data)
          args        (:clojure.spec.alpha/args data)]
      (str "🤕 \uD83D\uDC94️\n" (pred-fails problems)))))

(defn format-response
  [show-times? result]
  (let [{:keys [val tag ms]} result
        exception-data (check-exception val)]
    (cond
      exception-data
      (str "=> " exception-data "\n")

      (= tag :out)
      val

      (= tag :ret)
      (str (when show-times? (str ms " ms "))
           "=> " (format-nil-vals val) "\n"))))

;; Demo of live changes
;(defn format-responses
;  [show-times? {:keys [form prepl-response live-repl]}]
;  (str "POSTED Since Live? " (false? (nil? live-repl)) "\n"
;       form "\n"
;       (doall (apply str (map (partial format-response show-times?)
;                              prepl-response)))))

(defn format-responses
  [show-times? {:keys [form prepl-response]}]
  (str form "\n"
       (doall (apply str (map (partial format-response show-times?)
                              prepl-response)))))

(defn format-results
  [show-times? results]
  (doall (map (partial format-responses show-times?) results)))

(reg-event-fx
  ::clear-evals
  (fn [{:keys [db]} [_ _]]
    (when-let [code-mirror (:eval-code-mirror db)]
      {:db                                 (assoc db :eval-results [])
       ::code-mirror/set-code-mirror-value {:value       ""
                                            :code-mirror code-mirror}})))

(reg-event-fx
  ::eval-result
  (fn [{:keys [db]} [_ eval-result]]
    (let [code-mirror  (:eval-code-mirror db)
          show-times?  (true? (:show-times db))
          eval-results (conj (:eval-results db) eval-result)
          str-results  (apply str (reverse
                                    (format-results show-times? eval-results)))]
      {:db                                 (assoc db :eval-results eval-results)
       ::code-mirror/set-code-mirror-value {:value       str-results
                                            :code-mirror code-mirror}})))

(reg-event-fx
  ::show-times
  (fn [{:keys [db]} [_ show-times]]
    (let [code-mirror  (:eval-code-mirror db)
          show-times?  (true? show-times)
          eval-results (:eval-results db)
          str-results  (apply str (reverse
                                    (format-results show-times? eval-results)))]
      {:db                                 (assoc db :show-times show-times)
       ::code-mirror/set-code-mirror-value {:value       str-results
                                            :code-mirror code-mirror}})))

(reg-event-db
  ::eval-code-mirror
  (fn [db [_ code-mirror]]
    (assoc db :eval-code-mirror code-mirror)))

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
  (fn [{:keys [db]} [_ input]]
    (let [form-to-eval (if (string? input) input (:form input))]
      {:db              (assoc db :form-to-eval form-to-eval)
       ::send-repl-eval [:user form-to-eval]})))

(reg-fx
  ::server-login
  (fn [{:keys [login-options timeout]}]
    (chsk-send! [:reptile/login login-options] (or timeout 3000)
                (fn [result]
                  (if (= result :login-ok)
                    (re-frame/dispatch [::logged-in-user (:user login-options)])
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


;; ------------------------------------------------------------------

;; ---------------------- Network sync

;; Text
(reg-fx
  ::sync-current-form
  (fn [{:keys [form name timeout]}]
    (chsk-send! [:reptile/keystrokes {:form      form
                                      :user-name name}]
                (or timeout 3000))))

(reg-event-fx
  ::current-form
  (fn [{:keys [db]} [_ current-form]]
    (when-not (str/blank? (str/trim current-form))
      (let [local-repl-editor   (:local-repl-editor db)
            updated-repl-editor (assoc local-repl-editor :form current-form)]
        {:db                 (assoc db :local-repl-editor updated-repl-editor)
         ::sync-current-form updated-repl-editor}))))

;; ------------------------------------------------------------------

;; ---------------------- Editor default data

(defn styled-editor
  [editor color]
  (let [editor-key        (first (first editor))
        editor-name       (name editor-key)
        editor-properties (second (first editor))
        styled-properties (merge editor-properties
                                 {:abbr  (subs editor-name 0
                                               (min (count editor-name) 2))
                                  :style {:color color}})]
    {editor-key styled-properties}))

(defn editor-property-update
  [repl-editor-name editor]
  (let [editor-key     (first editor)
        editor-name    (name editor-key)
        properties     (second editor)
        default-editor {:name                editor-name
                        :local-repl-editor   (= repl-editor-name editor-name)
                        :network-repl-editor (false? (= repl-editor-name editor-name))
                        :visibility          true
                        :active              true
                        :last-active         (js/Date.now)}]
    {editor-key (merge properties default-editor)}))

; TODO - maintain a uniform ordering as editors are added and ensure variety of styling
(defn update-editor-defaults
  "Set various defaults for any new editors, leave existing editors untouched"
  [repl-editor-name editors]
  (let [new-users          (filter (comp nil? :last-active last) editors)
        property-update-fn (partial editor-property-update repl-editor-name)
        updated-editors    (map property-update-fn new-users)
        editor-colors      ["red" "blue" "black" "green" "orange" "gray"]
        styled-editors     (into {} (sort-by (comp :name last)
                                             (map #(styled-editor %1 %2)
                                                  updated-editors editor-colors)))]
    (merge editors styled-editors)))


;; ------------------------------------------------------------------

; {:your-name {:client-id 481d984d-fdb6-4ce9-a367-77ce5b5068f1, :observer false}}

;; ---------------------- Logged in editor
(reg-event-db
  ::logged-in-user
  (fn [db [_ user-name]]
    (assoc db :user user-name)))

(reg-event-db
  ::repl-editor-code-mirror
  (fn [db [_ code-mirror]]
    (let [local-repl-editor   (:local-repl-editor db)
          updated-repl-editor (assoc local-repl-editor :code-mirror code-mirror)]
      (assoc db :local-repl-editor updated-repl-editor))))

(reg-event-fx
  ::from-history
  (fn [{:keys [db]} [_ history-form]]
    (let [local-repl-editor   (:local-repl-editor db)
          updated-repl-editor (assoc local-repl-editor :form history-form)]
      {:db                            (assoc db :local-repl-editor updated-repl-editor)
       ::code-mirror/sync-code-mirror updated-repl-editor})))

;; ------------------------------------------------------------------

;; ---------------------- Logged in network user
(reg-event-db
  ::network-user
  (fn [db [_ user-name]]
    (assoc db :network-repl-editors (merge (:network-repl-editors db)
                                           {(keyword user-name)
                                            {:name user-name :editor true}}))))

;BUG??
; (reg-event-db
;  ::network-repl-editor
;  (fn [db [_ code-mirror editor-key]]
;    (let [network-repl-editor (get-in db [:network-repl-editors editor-key])
;          updated-editor      (assoc network-repl-editor :code-mirror code-mirror)]
;      (assoc db :network-repl-editors (merge (:network-repl-editors db)
;                                             {editor-key updated-editor})))))

;; ------------------------------------------------------------------

;; ---------------------- Editor visibility

(defn toggle-visibility
  [editor-key editor-properties]
  (let [visibility (false? (:visibility editor-properties))]
    {editor-key (assoc editor-properties :visibility visibility)}))

#_(defn check-active
    [editor inactivity-ms since]
    (let [inactivity-period (- since (:last-active editor))]
      (if (> inactivity-ms inactivity-period)
        (assoc editor :active true)
        (assoc editor :active false))))

#_(reg-event-db
    ::idle-check
    (fn [db [_ editor]]
      ; TODO - fix up to use network users, pass in ID and dissoc that from the list
      (let [inactivity-ms (:inactivity-ms db)
            editors       (:annotated-editors db)
            check-list    (filter #(not (= (:name %) editor)) editors)
            now           (js/Date.now)]
        (assoc db :annotated-editors (map #(check-active % inactivity-ms now) check-list)))))

(reg-event-fx
  ::network-user-visibility-toggle
  (fn [{:keys [db]} [_ editor-key]]
    (let [network-repl-editors (:network-repl-editors db)
          network-repl-editor  (get network-repl-editors editor-key)
          updated-repl-editor  (toggle-visibility editor-key network-repl-editor)
          network-repl-editors (merge network-repl-editors updated-repl-editor)]
      {:db                            (assoc db :network-repl-editors network-repl-editors)
       ::code-mirror/sync-code-mirror (get network-repl-editors editor-key)})))

;; Obtain an updated form from a network user
(reg-event-fx
  ::network-repl-editor-form-update
  (fn [{:keys [db]} [_ {:keys [user form]}]]
    (when-not (= user (:user db))
      (let [editor-key           (keyword user)
            network-repl-editors (:network-repl-editors db)
            network-repl-editor  (get network-repl-editors editor-key)
            updated-repl-editor  (assoc network-repl-editor :active true
                                                            :last-active (js/Date.now)
                                                            :form form)
            network-repl-editors (merge network-repl-editors
                                        {editor-key updated-repl-editor})]
        {:db                            (assoc db :network-repl-editors network-repl-editors)
         ::code-mirror/sync-code-mirror updated-repl-editor}))))

(reg-event-db
  ::network-repl-editor-code-mirror
  (fn [db [_ code-mirror editor-key]]
    (let [network-repl-editors (:network-repl-editors db)
          network-repl-editor  (get network-repl-editors editor-key)
          updated-repl-editor  (assoc network-repl-editor :code-mirror code-mirror)
          network-repl-editors (merge network-repl-editors
                                      {editor-key updated-repl-editor})]
      (assoc db :network-repl-editors network-repl-editors))))

(reg-event-db
  ::repl-editors
  (fn [db [_ repl-editors]]
    (let [local-user           (:user db)
          local-user-key       (keyword local-user)
          all-editors          (update-editor-defaults local-user repl-editors)
          local-repl-editor    (get all-editors local-user-key)
          network-repl-editors (dissoc all-editors local-user-key)]

      ; TODO - add back in once everything else stable
      ;(when (= 2 (count styled-editors))
      ;  ; Establish a recurring check whether the new editor is idle
      ;  (async/go-loop
      ;    []
      ;    (async/<! (async/timeout (* 30 1000)))
      ;    (re-frame/dispatch [::idle-check (last styled-editors)])
      ;    (recur)))

      (assoc db :local-repl-editor local-repl-editor
                :network-repl-editors network-repl-editors))))

