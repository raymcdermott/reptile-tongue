(ns reptile.tongue.events
  (:require
    goog.date.Date
    [cljs.core.specs.alpha]
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx]]
    [cljs.tools.reader.edn :as rdr]
    [cljs.core.async]
    [reptile.tongue.ws :as ws]
    [clojure.string :as str]
    [reptile.tongue.db :as db]
    [reptile.tongue.code-mirror :as code-mirror]))

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
      (ws/chsk-send! [:reptile/repl {:form   form
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
  (fn [{:keys [options timeout]}]
    (ws/chsk-send! [:reptile/login options] (or timeout 3000)
                   (fn [result]
                     (if (= result :login-ok)
                       (re-frame/dispatch [::logged-in-user (:user options)])
                       (js/alert "Login failed"))))))

(reg-event-fx
  ::login
  (fn [cofx [_ login-options]]
    {:db            (assoc (:db cofx) :proposed-user (:user login-options)
                                      :observer (:observer login-options)
                                      :user-name nil)
     ::server-login {:options login-options}}))

(reg-fx
  ::server-logout
  (fn [{:keys [options timeout]}]
    (ws/chsk-send! [:reptile/logout options] (or timeout 3000))))

(reg-event-fx
  ::logout
  (fn [{:keys [db]} _]
    {:db             (dissoc db :local-repl-editor :user)
     ::server-logout {:options (:name (:local-repl-editor db))}}))

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


;; ---------------------- Network sync

;; Text
(reg-fx
  ::sync-current-form
  (fn [{:keys [form name timeout]}]
    (ws/chsk-send! [:reptile/keystrokes {:form      form
                                         :user-name name}]
                   (or timeout 3000))))

(reg-event-fx
  ::current-form
  (fn [{:keys [db]} [_ current-form]]
    (when-not (str/blank? (str/trim current-form))
      (let [local-repl-editor   (:local-repl-editor db)
            updated-repl-editor (assoc local-repl-editor :form current-form)]
        {:db                 (assoc db :local-repl-editor updated-repl-editor
                                       :current-form current-form)
         ::sync-current-form updated-repl-editor}))))

;; ------------------------------------------------------------------

;; ---------------------- Editor default data

(defn styled-editor
  [editor color icon]
  (let [editor-key        (first (first editor))
        editor-name       (name editor-key)
        editor-properties (second (first editor))
        styled-properties (merge editor-properties
                                 {:abbr  (subs editor-name 0
                                               (min (count editor-name) 2))
                                  :style {:color color}
                                  :icon  icon})]
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

(defn editor-icons
  ([] (editor-icons :random false))
  ([& {:keys [random]}]
   (let [data    ["mood" "mood-bad" "run" "walk" "face" "male-female" "lamp" "cutlery"
                  "flower" "flower-alt" "coffee" "cake" "attachment" "attachment-alt"
                  "fire" "nature" "puzzle-piece" "drink" "truck" "car-wash" "bug"]
         sort-fn (if random (partial shuffle) (partial sort))]
     (sort-fn (map (partial str "zmdi-") data)))))

(defn colour-palette
  ([] (colour-palette :random false))
  ([& {:keys [random]}]
   (let [data    ["silver" "gray" "black" "red" "maroon" "olive" "lime"
                  "green" "aqua" "teal" "blue" "navy" "fuchsia" "purple"]
         sort-fn (if random (partial shuffle) (partial sort))]
     (sort-fn data))))

; TODO - maintain a uniform ordering as editors are added and ensure variety of styling
(defn update-editor-defaults
  "Set various defaults for any new editors, leave existing editors untouched"
  [repl-editor-name editors]
  (let [new-users          (filter (comp nil? :last-active last) editors)
        property-update-fn (partial editor-property-update repl-editor-name)
        updated-editors    (map property-update-fn new-users)
        icons              (editor-icons :random true)
        colours            (colour-palette :random true)
        styled-editors     (into {} (sort-by (comp :name last)
                                             (map #(styled-editor %1 %2 %3)
                                                  updated-editors colours icons)))]
    (merge editors styled-editors)))


;; ------------------------------------------------------------------

; {:your-name {:client-id 481d984d-fdb6-4ce9-a367-77ce5b5068f1, :observer false}}

;; ---------------------- Logged in editor
(reg-event-db
  ::logged-in-user
  (fn [db [_ user-name]]
    (assoc db :user user-name :local-repl-editor {:name user-name})))

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
      {:db                            (assoc db :local-repl-editor updated-repl-editor
                                                :current-form history-form)
       ::code-mirror/sync-code-mirror updated-repl-editor})))

;; ---------------------- Logged in network user
(reg-event-db
  ::network-user
  (fn [db [_ user-name]]
    (assoc db :network-repl-editors (merge (:network-repl-editors db)
                                           {(keyword user-name)
                                            {:name user-name :editor true}}))))

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
            ;_                    (println :pre network-repl-editors)
            network-repl-editors (merge network-repl-editors
                                        {editor-key updated-repl-editor})]
        ;(println :post network-repl-editors)
        {:db                            (assoc db :network-repl-editors network-repl-editors)
         ::code-mirror/sync-code-mirror updated-repl-editor}))))

(reg-fx
  ::save-gist
  (fn [kw]
    (println :save-gist kw)))

(reg-event-fx
  ::gist-persist
  (fn [{:keys [db]} _]
    {:db         (dissoc db :saved-gist false)
     ::save-gist :foo}))


(reg-event-db
  ::network-repl-editor-code-mirror
  (fn [db [_ code-mirror editor-key]]
    (let [network-repl-editors (:network-repl-editors db)
          network-repl-editor  (get network-repl-editors editor-key)
          updated-repl-editor  (assoc network-repl-editor :code-mirror code-mirror)
          network-repl-editors (merge network-repl-editors
                                      {editor-key updated-repl-editor})]
      (assoc db :network-repl-editors network-repl-editors))))

; repl-editors is supplied by the server. These transformations ensure that the same list
; is maintained on the client when users are newly logged in or logged out.
(reg-event-db
  ::repl-editors
  (fn [db [_ repl-editors]]
    (let [local-user               (:user db)
          network-repl-editor-keys (keys (dissoc (into {} repl-editors) (keyword local-user)))
          updated-nreks            (select-keys (:network-repl-editors db) network-repl-editor-keys)
          network-repl-editors     (merge repl-editors updated-nreks)
          local-user-key           (keyword local-user)
          all-editors              (update-editor-defaults local-user network-repl-editors)
          local-editor             (get all-editors local-user-key)
          local-repl-editor        (merge local-editor (:local-repl-editor db))
          network-repl-editors     (dissoc all-editors local-user-key)]

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

