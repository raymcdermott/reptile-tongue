(ns reptile.tongue.config)

; This can be re-defined with the :closure-defines compiler option
; -- see min.cljs.edn as an example

(goog-define TAIL_SERVER "reptile.extemporay.io")
(goog-define TAIL_SERVER_PORT "8888")

(def debug? ^boolean goog.DEBUG)

(def browser-port (.-port js/location))

(def browser-host (.-hostname js/location))

; default figwheel port
(def figwheel-default-port "9500")

; local server port
(def local-server-port TAIL_SERVER_PORT)

(def server-port (when (= figwheel-default-port browser-port)
                   local-server-port))

(def server-host (if (not (= browser-host "localhost"))
                   TAIL_SERVER
                   (if server-port
                     (str browser-host ":" server-port)
                     browser-host)))