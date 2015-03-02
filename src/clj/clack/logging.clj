(ns clack.logging
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre]))

(def log-file "clack_log.log")
(io/delete-file log-file :quiet)

;; refer to timbre namespaces
(timbre/refer-timbre)

(timbre/set-config! [:appenders :standard-out :enabled?] false) ;; Turn off console logging.
(timbre/set-config! [:appenders :spit :enabled?] true) ;; turn on file logging.
(timbre/set-config! [:shared-appender-config :spit-filename] log-file)

(timbre/set-level! :debug)
