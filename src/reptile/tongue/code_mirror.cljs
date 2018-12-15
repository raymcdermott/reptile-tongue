(ns reptile.tongue.code-mirror
  "Code mirror support"
  (:require
    [re-frame.core :refer [reg-fx dispatch]]
    [cljsjs.codemirror]
    [cljsjs.codemirror.mode.clojure]
    [cljsjs.codemirror.addon.edit.matchbrackets]
    [cljsjs.codemirror.addon.hint.show-hint]
    [cljsjs.parinfer-codemirror]
    [cljsjs.parinfer]))

(defn text-area
  [id]
  (fn
    []
    [:textarea {:id            id
                :auto-complete false
                :default-value ""}]))

(defn parinfer
  [dom-node config]
  (let [editor-options (clj->js (merge {:mode :clojure} (:options config)))
        code-mirror    (js/CodeMirror.fromTextArea dom-node editor-options)
        editor-width   (get-in config [:size :width] "100%")
        editor-height  (get-in config [:size :height] "100%")]
    (.setSize code-mirror editor-width editor-height)
    (js/parinferCodeMirror.init code-mirror)
    code-mirror))

(defn sync-user-code-mirror!
  "Update the user's most recent input to their code mirror"
  [{:keys [code-mirror form]}]
  (.setValue code-mirror form))

(defn shown-item
  "Open the documentation panel"
  []
  (dispatch [:reptile.tongue.events/show-doc-panel true]))

(defn select-item
  "Documentation for the selected completion"
  [completion _]
  (dispatch [:reptile.tongue.events/doc-text
             (js->clj completion :keywordize-keys true)]))

(defn pick-item
  "Documentation for the picked completion"
  [completion]
  (dispatch [:reptile.tongue.events/doc-text
             (js->clj completion :keywordize-keys true)]))

(defn close-item
  "Close the documentation panel"
  []
  (dispatch [:reptile.tongue.events/show-doc-panel false]))

(defn clojure-completions
  [completions editor _]
  (let [candidates (clj->js (map
                              (fn [c]
                                {:text        (:candidate c)
                                 :displayText (str (:candidate c) " " (:args c))
                                 :docs        (:docs c)})
                              completions))
        cur        (.getCursor editor)
        line-num   (.-line cur)
        end        (.-ch cur)
        cur-line   (.getLine editor line-num)
        start      (loop [start end]
                     (if (.test #"[\w$]+" (.charAt cur-line (dec start)))
                       (recur (dec start))
                       start))
        result     (clj->js {:list candidates
                             :from (js/CodeMirror.Pos line-num start)
                             :to   (js/CodeMirror.Pos line-num end)})]

    (.on js/CodeMirror result "shown" shown-item)
    (.on js/CodeMirror result "select" select-item)
    (.on js/CodeMirror result "pick" pick-item)
    (.on js/CodeMirror result "close" close-item)

    result))

(defn trigger-autocomplete [{:keys [code-mirror completions]}]
  (let [hint-fn (partial clojure-completions completions)]
    (js/setTimeout
      (fn []
        (.showHint code-mirror
                   (clj->js {:hint           hint-fn
                             :completeSingle false})))
      100)
    js/CodeMirror.Pass))

(reg-fx
  ::sync-code-mirror
  (fn [user]
    (sync-user-code-mirror! user)))

(reg-fx
  ::auto-complete
  (fn [user]
    (when-not (empty? (map :candidate (:completions user)))
      (trigger-autocomplete user))))

(reg-fx
  ::set-code-mirror-value
  (fn [{:keys [code-mirror value]}]
    (.setValue code-mirror value)))

