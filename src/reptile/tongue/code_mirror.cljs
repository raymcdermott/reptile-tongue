(ns reptile.tongue.code-mirror
  "Code mirror support"
  (:require
    [re-frame.core :refer [reg-fx]]
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

(defn clojure-completions
  [{:keys [completions]}]
  (fn [editor _]
    (let [completion-list (map :candidate completions)
          candidates      (if (empty? completion-list) [] completion-list)
          cur             (.getCursor editor)
          line-num        (.-line cur)
          end             (.-ch cur)
          cur-line        (.getLine editor line-num)
          start           (loop [start end]
                            (if (.test #"[\w$]+" (.charAt cur-line (dec start)))
                              (recur (dec start))
                              start))]
      (clj->js {:list candidates
                :from (js/CodeMirror.Pos line-num start)
                :to   (js/CodeMirror.Pos line-num end)}))))

(defn trigger-autocomplete [{:keys [code-mirror] :as user}]
  (let [hint-fn (clojure-completions user)]
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

