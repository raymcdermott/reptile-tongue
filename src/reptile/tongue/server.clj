(ns reptile.tongue.server
  (:require [reptile.server.http :as http]))

(let [port   8888
      secret "warm-blooded-lizards-rock"]
  (println "Starting server")
  (http/start-reptile-server port secret))

(defn handler [request] request)