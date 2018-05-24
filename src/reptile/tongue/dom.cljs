(ns reptile.tongue.dom
  (:require [cljs.spec.alpha]
            [goog.object :as gobj]))

(defn get-selection
  "Returns the objects related to selection for the given element. If full-selection? is true,
it will use rangy instead of the native selection API in order to get the beginning and ending
of the selection (it is, however, much slower)."
  [element full-selection?]
  {:element element
   :cursor-position
            (cond
              full-selection?
              (let [selection (.getSelection js/rangy)
                    ranges    (.saveCharacterRanges selection element)]
                (if-let [char-range (some-> ranges (aget 0) (gobj/get "characterRange"))]
                  [(gobj/get char-range "start") (gobj/get char-range "end")]
                  [0 0]))
              (= 0 (.-rangeCount (.getSelection js/window)))
              [0 0]
              :else
              (let [selection       (.getSelection js/window)
                    range           (.getRangeAt selection 0)
                    pre-caret-range (doto (.cloneRange range)
                                      (.selectNodeContents element)
                                      (.setEnd (.-endContainer range) (.-endOffset range)))
                    pos             (-> pre-caret-range .toString .-length)]
                [pos pos]))})

(defn get-cursor-position
  "Returns the cursor position."
  [element full-selection?]
  (-> element (get-selection full-selection?) :cursor-position))

(defn set-cursor-position!
  "Moves the cursor to the specified position."
  [element position]
  (let [[start-pos end-pos] position
        selection  (.getSelection js/rangy)
        char-range #js {:start start-pos :end end-pos}
        range      #js {:characterRange   char-range
                        :backward         false
                        :characterOptions nil}
        ranges     (array range)]
    (.restoreCharacterRanges selection element ranges)))

(defn paren-mode
  "Runs paren mode on the given text."
  [text x line]
  (let [res (.parenMode js/parinfer text #js {:cursorLine line :cursorX x})]
    {:x (aget res "cursorX") :text (aget res "text")}))
