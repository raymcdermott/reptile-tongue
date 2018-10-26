(ns reptile.tongue.db)

(defonce default-db
         {:name          "reptile"
          ; 1 minute
          :inactivity-ms (* 60 1000)})
