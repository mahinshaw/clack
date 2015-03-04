(ns clack.logging
  (:require [clojure.java.io :as io]
            [taoensso.timbre :refer :all]))

(defn refer-logging [] (refer-timbre))

(def log-file "clack_log.log")
(io/delete-file log-file :quiet)

(set-config! [:appenders :standard-out :enabled?] true) ;; turn on logging to console
(set-config! [:appenders :spit :enabled?] true) ;; turn on file logging.
(set-config! [:shared-appender-config :spit-filename] log-file)

(set-level! :debug)
