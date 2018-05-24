(ns reptile.tongue.config)

(def debug?
  ^boolean goog.DEBUG)

(def browser-port
  (.-port js/location))

(def browser-host
  (.-hostname js/location))

; default figwheel port
(def figwheel-default-port "3449")

; default local server port
(def server-default-port "9090")

(def server-port
  ; use the default WS server port when serving from figwheel
  (when (= figwheel-default-port browser-port)
    server-default-port))

(def server-host
  (if (not (= browser-host "localhost"))
    "reptile.extemporay.io"
    (if server-port
      (str browser-host ":" server-port)
      browser-host)))

