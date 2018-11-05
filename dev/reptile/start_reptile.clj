(ns reptile.start-reptile
  (:use [reptile.reload-server]))


;------------------- ************* -------------------
;
; Bootstrap when the namespace is loaded
;
;------------------- ************* -------------------

(boot-and-watch-fs! "/opt/reptile/shared/reptile-body/src"
                    58885 "warm-blooded-lizards-rock")

