(ns reptile.tongue.config)

; This can be re-defined with the :closure-defines compiler option
; -- see min.cljs.edn as an example

(goog-define TAIL_SERVER "reptile.extemporay.io")
(goog-define TAIL_SERVER_PORT "58885")

(defonce debug? ^boolean goog.DEBUG)

(defonce browser-port (.-port js/location))

(defonce browser-host (.-hostname js/location))

; default figwheel port
(defonce figwheel-default-port "9500")

; local server port
(defonce local-server-port TAIL_SERVER_PORT)

(defonce server-port (when (= figwheel-default-port browser-port)
                       local-server-port))

(defonce server-host (if (not (= browser-host "localhost"))
                       TAIL_SERVER
                       (if server-port
                         (str browser-host ":" server-port)
                         browser-host)))
