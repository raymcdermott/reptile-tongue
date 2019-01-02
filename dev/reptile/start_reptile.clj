(ns reptile.start-reptile
  (:use [reptile.body.reload-server]))


;------------------- ************* -------------------
;
; Bootstrap when the namespace is loaded
;
;------------------- ************* -------------------

(boot-and-watch-fs! "/Users/ray/dev/reptile-house/body/src"
                    58885 "warm-blooded-lizards-rock")

