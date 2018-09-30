(ns reptile.tongue.code-mirror
  (:require [cljsjs.codemirror]
            [cljsjs.codemirror.mode.clojure]
            [cljsjs.codemirror.addon.edit.matchbrackets]
            [cljsjs.parinfer-codemirror]
            [cljsjs.parinfer]))

;; CodeMirror support

(defn text-area
  [id]
  (fn [] [:textarea {:id            id
                     :auto-complete false
                     :default-value ""}]))

(defn parinfer
  [dom-node config]
  (let [editor-options (clj->js (merge {:mode :clojure} (:options config)))
        code-mirror    (js/CodeMirror.fromTextArea dom-node editor-options)
        editor-height  (get-in config [:size :height] "100%")
        editor-width   (get-in config [:size :width] "100%")]
    (.setSize code-mirror editor-height editor-width)
    (js/parinferCodeMirror.init code-mirror)
    code-mirror))

