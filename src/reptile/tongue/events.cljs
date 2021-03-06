(ns reptile.tongue.events
  (:require
    goog.date.Date
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx]]
    [cljs.tools.reader.edn :as rdr]
    [clojure.core.async]
    [clojure.string :as string]
    [reptile.tongue.helpers :refer [js->cljs]]
    [reptile.tongue.ws :as ws]
    [reptile.tongue.db :as db]
    [reptile.tongue.code-mirror :as code-mirror]
    [taoensso.sente :as sente]))

(def default-server-timeout 3000)

; --- Events ---
(reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  ::network-status
  (fn [db [_ status]]
    (assoc db :network-status status)))

(reg-event-db
  ::network-user-id
  (fn [db [_ network-user-id]]
    (assoc db :network-user-id network-user-id)))

(defn pred-fails
  [problems]
  (some->> problems
           (map #(str "🤔  " (:val %) " is not " (:pred %)))
           (interpose "\n")
           (apply str)))

(defn default-reptile-tag-reader
  [tag val]
  {:nk-tag tag :nk-val (rdr/read-string (str val))})

(defn read-exception
  [val]
  (try
    (let [reader-opts {:default default-reptile-tag-reader}]
      (rdr/read-string reader-opts val))
    (catch :default _ignore-reader-errors)))

(def bugs "🐛 🐞 🐜\n")

;; TODO integrate a nice spec formatting library after 1.10 is GA
(defn check-exception
  [val]
  (let [{:keys [cause via trace data phase] :as exc} (read-exception val)
        problems   (:clojure.spec.alpha/problems data)
        spec       (:clojure.spec.alpha/spec data)
        value      (:clojure.spec.alpha/value data)
        args       (:clojure.spec.alpha/args data)
        spec-fails (and problems (pred-fails problems))]
    (when-let [problem (or spec-fails cause)]
      (str bugs problem))))

(defn format-response
  [show-times? result]
  (let [{:keys [val form tag ms]} result
        exception-data (check-exception val)]
    (cond
      exception-data
      (str form "\n"
           "=> " exception-data "\n\n")

      (= tag :err)
      (str form "\n"
           bugs val "\n\n")

      (= tag :out)
      (str val)

      (= tag :ret)
      (str form "\n"
           (when show-times? (str ms " ms "))
           "=> " (or val "nil") "\n\n"))))

(defn format-responses
  [show-times? {:keys [prepl-response]}]
  (doall (apply str (map (partial format-response show-times?)
                         prepl-response))))

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
          history      (let [evals (->> eval-results (map :form) distinct)]
                         (map (fn [h n]
                                {:index n :history h})
                              (reverse evals) (range)))
          str-results  (apply str (reverse
                                    (format-results show-times? eval-results)))]
      {:db                                 (assoc db :eval-results eval-results
                                                     :history history)
       ::code-mirror/set-code-mirror-value {:value       str-results
                                            :code-mirror code-mirror}})))

(reg-event-db
  ::history-item
  (fn [db [_ index]]
    (assoc db :history-item index)))

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
    (when-not (string/blank? form)
      (ws/chsk-send! [:reptile/repl {:form   form
                                     :source source
                                     :forms  form}]
                     (or (:timeout form) default-server-timeout)))))

(reg-event-fx
  ::eval
  (fn [{:keys [db]} [_ input]]
    (let [form-to-eval (if (string? input) input (:form input))]
      {:db              (assoc db :form-to-eval form-to-eval)
       ::send-repl-eval [:user form-to-eval]})))

(reg-fx
  ::get-team-data
  (fn []
    (ws/chsk-send! [:reptile/team-random-data]
                   default-server-timeout
                   (fn [reply]
                     (if (and (sente/cb-success? reply))
                       (re-frame/dispatch [::team-data reply])
                       (js/alert "Cannot start the team"))))))

(reg-event-fx
  ::team-bootstrap
  (fn [{:keys [db]}]
    {:db (assoc db :team-defined false)
     ::get-team-data []}))

(reg-event-db
  ::team-data
  (fn [db [_ team-data]]
    (assoc db :team-data team-data)))

(reg-event-db
  ::show-team-data
  (fn [db [_ show-team-data]]
    (assoc db :show-team-data show-team-data)))

(reg-fx
  ::server-login
  (fn [{:keys [options timeout]}]
    (ws/chsk-send! [:reptile/login options] (or timeout default-server-timeout)
                   (fn [reply]
                     (if (and (sente/cb-success? reply) (= reply :login-ok))
                       (re-frame/dispatch [::logged-in-user (:user options)])
                       (js/alert "Login failed"))))))

(reg-event-fx
  ::login
  (fn [{:keys [db]} [_ login-options]]
    (let [network-user-id (:network-user-id db)]
      {:db            (assoc db :proposed-user (:user login-options)
                                :observer (:observer login-options)
                                :user-name nil)
       ::server-login {:options (assoc login-options
                                  :network-user-id network-user-id)}})))

(reg-fx
  ::server-logout
  (fn [{:keys [options timeout]}]
    (ws/chsk-send! [:reptile/logout options] (or timeout default-server-timeout))))

(reg-event-fx
  ::logout
  (fn [{:keys [db]} _]
    {:db             (dissoc db :local-repl-editor :user)
     ::server-logout {:options (:name (:local-repl-editor db))}}))

(reg-event-db
  ::show-add-lib-panel
  (fn [db [_ show?]]
    (assoc db :show-add-lib-panel show?)))

(reg-event-db
  ::show-doc-panel
  (fn [db [_ show?]]
    ;(prn ::show-doc-panel show? tag)
    (assoc db :doc-show? show?)))

(reg-event-db
  ::doc-text
  (fn [db [_ text]]
    ;(prn ::doc-text text)
    (assoc db :doc-text text)))

(reg-event-fx
  ::add-lib
  (fn [cofx [_ {:keys [name version url sha maven] :as lib}]]
    (let [use-ns   "(use 'clojure.tools.deps.alpha.repl)"
          lib-spec (str "(add-lib '" (string/trim name) " {"
                        (if maven
                          (str ":mvn/version \"" (string/trim version) "\"")
                          (str ":git/url \"" (string/trim url) "\" :sha \"" (string/trim sha) "\""))
                        "})")]
      {:db              (assoc (:db cofx) :proposed-lib lib)
       ::send-repl-eval [:system (str use-ns "\n" lib-spec)]})))


;; ------------------------------------------------------------------

;; ---------------------- Network sync

(defn changed-part-of-line
  "Returns the substring up to the char that is changed
  on the changed line from within `form`"
  [form {:keys [to-line to-ch] :as change-data}]
  (-> form
      string/split-lines
      (nth to-line)
      (subs 0 (inc to-ch))))

(defn completion-token
  "Given the form and change-data, find the token that is the target for completion"
  [form {:keys [text to-ch] :as change-data}]
  (when (re-find #"\w+" (apply str text))                   ; text may include spaces
    (let [token     (->> change-data
                         (changed-part-of-line form)
                         clojure.string/reverse             ; reverse the line
                         (re-find #"\w+")                   ; look backwards for the word
                         clojure.string/reverse)            ; set everything back to normal
          token-len (count token)]
      (assoc change-data :token token
                         :token-len token-len
                         :token-start (- (inc to-ch) token-len)))))

(defn prefixed-form
  [form {:keys [to-line token token-start] :as change-data}]
  (let [form-lines    (string/split-lines form)
        line-to-edit  (nth form-lines to-line)
        prefix-line   (str (subs line-to-edit 0 token-start)
                           (-> line-to-edit
                               (subs token-start)
                               (string/replace-first token "__prefix__")))
        prefixed-form (->> (conj (drop (inc to-line) form-lines)
                                 prefix-line
                                 (take to-line form-lines))
                           flatten
                           (interpose "\n")
                           string/join)]
    (assoc change-data :prefixed-form prefixed-form)))

(defn mark-completion-prefix
  [current-form change-data]
  (->> change-data
       (completion-token current-form)
       (prefixed-form current-form)))

;; Text
(reg-fx
  ::sync-current-form
  (fn [[{:keys [form name timeout]} prefixed-form to-complete]]
    (ws/chsk-send! [:reptile/keystrokes {:form          form
                                         :prefixed-form prefixed-form
                                         :to-complete   to-complete
                                         :user-name     name}]
                   (or timeout 3000))))

(defn change->data
  [{:keys [from to origin] :as change-data}]
  (assoc change-data :from-line (.-line from)
                     :from-ch (.-ch from)
                     :to-line (.-line to)
                     :to-ch (.-ch to)
                     :source (if (= origin "+input") :user :api)))

(reg-event-fx
  ::current-form
  (fn [{:keys [db]} [_ current-form change-object]]
    (when-not (string/blank? (string/trim current-form))
      (let [local-repl-editor   (:local-repl-editor db)
            change-data         (change->data (js->cljs change-object))
            {:keys [token prefixed-form]} (mark-completion-prefix current-form change-data)
            updated-repl-editor (assoc local-repl-editor :form current-form)]
        {:db                 (assoc db :local-repl-editor updated-repl-editor
                                       :current-form current-form
                                       :change-data change-data
                                       :to-complete token)
         ::sync-current-form [updated-repl-editor prefixed-form token]}))))

;; favour code-mirror hints
(reg-event-db
  ::current-word
  (fn [db [_ change-data]]

    ;; TODO - work out the completions

    ;; conj the texts until non [a-z-]
    ;; check for completions on each keystroke
    ;; something like
    ;; (def core-ns (map first (ns-publics 'clojure.core)))
    ;; (sort (filter #(clojure.string/starts-with? % "re-") core-ns))
    ;; set completions on the db
    ;; unset completions when not core letter

    ;; The server should include current ns defs on each call
    ;; a bit wasteful but there are rarely going to be > 10
    ;; and we can optimise down the line

    ;; any newly included namespaces should be tracked for inclusion
    ;; in the list. This could make the payload bigger so maybe we do maintain
    ;; another atom / transmission route

    ;; then combine defs from the user ns with the clojure.core

    (assoc db :current-word change-data)

    )

  )
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
  (fn [{:keys [db]} [_ index]]
    (let [local-repl-editor   (:local-repl-editor db)
          history             (:history db)
          history-item        (nth history index)
          history-form        (:history history-item)
          updated-repl-editor (assoc local-repl-editor :form history-form)]
      {:db                            (assoc db :local-repl-editor updated-repl-editor
                                                :current-form history-form
                                                :history-item history-item)
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
  (fn [{:keys [db]} [_ {:keys [user form completions]}]]
    (if (= user (:user db))
      (let [editor           (:local-repl-editor db)
            with-completions (assoc editor :completions completions
                                           :change-data (:change-data db)
                                           :to-complete (:to-complete db))]
        ;(println ::network-repl-editor-form-update (:change-data db))
        {:db                         (assoc db :local-repl-editor with-completions)
         ::code-mirror/auto-complete with-completions})
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

(reg-event-db
  ::network-repl-editor-code-mirror
  (fn [db [_ code-mirror editor-key]]
    (let [network-repl-editors (:network-repl-editors db)
          network-repl-editor  (get network-repl-editors editor-key)
          updated-repl-editor  (assoc network-repl-editor :code-mirror code-mirror)
          network-repl-editors (merge network-repl-editors
                                      {editor-key updated-repl-editor})]
      (assoc db :network-repl-editors network-repl-editors))))

; repl-editors is supplied by the server. These transformations ensure that the same
; list is maintained on the client when users are newly logged in or logged out.
; Each repl-editor has a network-user-id provided by the server for each connection
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

