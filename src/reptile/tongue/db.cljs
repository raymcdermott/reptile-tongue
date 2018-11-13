(ns reptile.tongue.db
  (:require [reptile.tongue.config :as config]))


(goog-define auth0-client-id "zCOgyeLeTYwvsDW2noB-R0kUY8fGTtl0")
(goog-define auth0-domain "extemporary.auth0.com")
(goog-define auth0-audience "https://extemporary.auth0.com/userinfo")

(def auth0-config {:client-id    auth0-client-id
                   :domain       auth0-domain
                   :audience     auth0-audience})

(defonce default-db {:auth0-config   auth0-config
                     :browser-config {:port config/browser-port
                                      :host config/browser-host
                                      :url  config/browser-url}})

