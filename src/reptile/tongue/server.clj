(ns reptile.tongue.server
  (:require [reptile.server.http :as http]))

(try
  ; Need DynamicClassLoader to support add-lib
  (let [port           8888
        secret         "warm-blooded-lizards-rock"
        current-thread (Thread/currentThread)
        cl             (.getContextClassLoader current-thread)]
    (.setContextClassLoader current-thread (clojure.lang.DynamicClassLoader. cl))
    (http/start-reptile-server port secret))
  (catch Exception e (str "ClassLoader issue - caught exception: " (.getMessage e))))

(defn handler [request] request)