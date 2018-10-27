(ns reptile.dev.start-reptile
  (:use [reptile.dev.reload-server]))


;------------------- ************* -------------------
;
; Bootstrap when the namespace is loaded
;
;------------------- ************* -------------------

(boot-and-watch-fs! "../reptile-lib/src" 58885 "warm-blooded-lizards-rock")

