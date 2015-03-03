(ns clack.logging
  (:require [clojure.java.io :as io]
            [taoensso.timbre :refer :all]))

(def log-file "clack_log.log")
(io/delete-file log-file :quiet)

(timbre/set-config! [:appenders :standard-out :enabled?] true) ;; turn on logging to console
(timbre/set-config! [:appenders :spit :enabled?] true) ;; turn on file logging.
(timbre/set-config! [:shared-appender-config :spit-filename] log-file)

(timbre/set-level! :debug)
