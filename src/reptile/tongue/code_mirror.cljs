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
  [{:keys [code-mirror form] :as user}]
  (.setValue code-mirror form))

(reg-fx
  ::sync-code-mirror
  (fn [user]
    (sync-user-code-mirror! user)))

(reg-fx
  ::set-code-mirror-value
  (fn [{:keys [code-mirror value]}]
    (.setValue code-mirror value)))

